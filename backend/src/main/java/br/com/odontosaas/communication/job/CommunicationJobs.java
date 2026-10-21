package br.com.odontosaas.communication.job;

import br.com.odontosaas.communication.repository.MessageRepository;
import br.com.odontosaas.communication.repository.MessageRepository.DueReminderRow;
import br.com.odontosaas.communication.repository.MessageRepository.PendingMessageRow;
import br.com.odontosaas.communication.service.CommunicationService;
import br.com.odontosaas.scheduling.service.AppointmentLinks;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rotinas agendadas: geração de lembretes e envio da fila. Rodam clínica por clínica, cada uma em seu próprio
 * contexto de tenant (o RLS continua valendo para os jobs).
 */
@Component
public class CommunicationJobs {

    private static final Logger log = LoggerFactory.getLogger(CommunicationJobs.class);
    private static final int MAX_ATTEMPTS = 5;

    private final MessageRepository messages;
    private final CommunicationService communication;
    private final AppointmentLinks links;
    private final JavaMailSender mail;
    private final TransactionTemplate tx;
    private final String from;
    private final String publicUrl;

    public CommunicationJobs(
            MessageRepository messages,
            CommunicationService communication,
            AppointmentLinks links,
            JavaMailSender mail,
            TransactionTemplate tx,
            @Value("${app.mail.from}") String from,
            @Value("${app.public-url}") String publicUrl) {
        this.messages = messages;
        this.communication = communication;
        this.links = links;
        this.mail = mail;
        this.tx = tx;
        this.from = from;
        this.publicUrl = publicUrl;
    }

    private List<UUID> activeTenants() {
        return messages.activeTenantIds();
    }

    /** Cria lembretes para consultas dentro da antecedência configurada pela clínica (padrão 24h). */
    public int generateReminders() {
        int total = 0;
        for (UUID tenantId : activeTenants()) {
            total += TenantContext.runAs(
                    tenantId,
                    null,
                    () -> tx.execute(status -> {
                        List<DueReminderRow> due = messages.dueReminders(tenantId);
                        for (DueReminderRow d : due) {
                            Map<String, String> fields = communication.appointmentFields(tenantId, d.id());
                            fields.put("link", publicUrl + "/consulta#token=" + links.issueToken(d.id()));
                            communication.enqueue(d.patientId(), d.id(), "APPOINTMENT_REMINDER", fields);
                            links.markReminderSent(d.id());
                        }
                        return due.size();
                    }));
        }
        return total;
    }

    /** Envia as mensagens pendentes. Falhas são registradas; após 5 tentativas a mensagem fica como FAILED. */
    public int dispatch() {
        int sent = 0;
        for (UUID tenantId : activeTenants()) {
            sent += TenantContext.runAs(tenantId, null, () -> {
                List<PendingMessageRow> pending = messages.pending(tenantId);
                int ok = 0;
                for (PendingMessageRow p : pending) {
                    try {
                        SimpleMailMessage m = new SimpleMailMessage();
                        m.setFrom(from);
                        m.setTo(p.toAddress());
                        m.setSubject(p.subject());
                        m.setText(p.body());
                        mail.send(m);
                        messages.markSent(tenantId, p.id());
                        ok++;
                    } catch (MailException e) {
                        int attempts = p.attempts() + 1;
                        messages.recordFailure(
                                tenantId,
                                p.id(),
                                attempts,
                                e.getClass().getSimpleName(),
                                attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING");
                        log.warn(
                                "Falha ao enviar mensagem {} (tentativa {}): {}",
                                p.id(),
                                attempts,
                                e.getClass().getSimpleName());
                    }
                }
                return ok;
            });
        }
        return sent;
    }

    /** Agendamento automático (desligado nos testes por app.jobs.enabled=false). */
    @Component
    @ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final CommunicationJobs jobs;

        Schedule(CommunicationJobs jobs) {
            this.jobs = jobs;
        }

        @Scheduled(fixedDelayString = "${app.jobs.reminders-delay:PT5M}", initialDelayString = "PT30S")
        void reminders() {
            try {
                jobs.generateReminders();
            } catch (RuntimeException e) {
                log.error("Falha ao gerar lembretes", e);
            }
        }

        @Scheduled(fixedDelayString = "${app.jobs.dispatch-delay:PT1M}", initialDelayString = "PT20S")
        void dispatch() {
            try {
                jobs.dispatch();
            } catch (RuntimeException e) {
                log.error("Falha ao enviar fila de mensagens", e);
            }
        }
    }
}
