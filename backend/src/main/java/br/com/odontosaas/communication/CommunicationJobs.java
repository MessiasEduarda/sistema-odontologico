package br.com.odontosaas.communication;

import br.com.odontosaas.scheduling.AppointmentLinks;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private final JdbcClient jdbc;
    private final CommunicationService communication;
    private final AppointmentLinks links;
    private final JavaMailSender mail;
    private final TransactionTemplate tx;
    private final String from;
    private final String publicUrl;

    public CommunicationJobs(
            JdbcClient jdbc,
            CommunicationService communication,
            AppointmentLinks links,
            JavaMailSender mail,
            TransactionTemplate tx,
            @Value("${app.mail.from}") String from,
            @Value("${app.public-url}") String publicUrl) {
        this.jdbc = jdbc;
        this.communication = communication;
        this.links = links;
        this.mail = mail;
        this.tx = tx;
        this.from = from;
        this.publicUrl = publicUrl;
    }

    private List<UUID> activeTenants() {
        return jdbc.sql("select id from tenants where status = 'ACTIVE'")
                .query(UUID.class)
                .list();
    }

    /** Cria lembretes para consultas dentro da antecedência configurada pela clínica (padrão 24h). */
    public int generateReminders() {
        int total = 0;
        for (UUID tenantId : activeTenants()) {
            total += TenantContext.runAs(
                    tenantId,
                    null,
                    () -> tx.execute(status -> {
                        record Due(UUID id, UUID patientId) {}
                        List<Due> due =
                                jdbc.sql("""
                                select a.id, a.patient_id from appointments a
                                left join clinic_settings s on s.tenant_id = a.tenant_id
                                where a.tenant_id = ? and coalesce(s.reminders_enabled, true)
                                  and a.status in ('SCHEDULED', 'CONFIRMED') and a.reminder_sent_at is null
                                  and a.starts_at > now()
                                  and a.starts_at <= now() + make_interval(hours => coalesce(s.reminder_hours_before, 24))
                                """).param(tenantId).query(Due.class).list();
                        for (Due d : due) {
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
                record Pending(UUID id, String toAddress, String subject, String body, int attempts) {}
                List<Pending> pending =
                        jdbc.sql("""
                                select id, to_address, subject, body, attempts from outbound_messages
                                where tenant_id = ? and status = 'PENDING' order by created_at limit 50
                                """).param(tenantId).query(Pending.class).list();
                int ok = 0;
                for (Pending p : pending) {
                    try {
                        SimpleMailMessage m = new SimpleMailMessage();
                        m.setFrom(from);
                        m.setTo(p.toAddress());
                        m.setSubject(p.subject());
                        m.setText(p.body());
                        mail.send(m);
                        jdbc.sql("update outbound_messages set status = 'SENT', sent_at = now(),"
                                        + " attempts = attempts + 1 where tenant_id = ? and id = ?")
                                .params(tenantId, p.id())
                                .update();
                        ok++;
                    } catch (MailException e) {
                        int attempts = p.attempts() + 1;
                        jdbc.sql("update outbound_messages set attempts = ?, last_error = ?, status = ?"
                                        + " where tenant_id = ? and id = ?")
                                .params(
                                        attempts,
                                        e.getClass().getSimpleName(),
                                        attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING",
                                        tenantId,
                                        p.id())
                                .update();
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
