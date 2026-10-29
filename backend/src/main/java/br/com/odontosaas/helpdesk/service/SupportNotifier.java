package br.com.odontosaas.helpdesk.service;

import br.com.odontosaas.helpdesk.repository.SupportRepository;
import br.com.odontosaas.helpdesk.repository.SupportRepository.JobTicket;
import br.com.odontosaas.helpdesk.repository.SupportRepository.PendingNotification;
import br.com.odontosaas.helpdesk.repository.SupportRepository.TicketRow;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.mail.MailDelivery;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
public class SupportNotifier {

    static final int MAX_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(SupportNotifier.class);

    private final SupportRepository repository;
    private final MailDelivery mail;
    private final String from;
    private final String publicUrl;
    private final List<String> extraRecipients;

    public SupportNotifier(
            SupportRepository repository,
            MailDelivery mail,
            @Value("${app.mail.from}") String from,
            @Value("${app.public-url}") String publicUrl,
            @Value("${app.support.notify-email:}") String notifyEmail) {
        this.repository = repository;
        this.mail = mail;
        this.from = from;
        this.publicUrl = publicUrl;
        this.extraRecipients = Arrays.stream(notifyEmail.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void ticketOpened(TicketRow t) {
        toSupport(
                t.id(),
                "[%s] Novo chamado %s: %s".formatted(priorityLabel(t.priority()), t.protocol(), t.subject()),
                """
                Novo chamado aberto pela %s.

                Protocolo: %s
                Assunto: %s
                Categoria: %s
                Prioridade: %s
                Aberto por: %s
                Primeira resposta até: %s

                Abra o chamado: %s
                """.formatted(
                                t.clinicName(),
                                t.protocol(),
                                t.subject(),
                                categoryLabel(t.category()),
                                priorityLabel(t.priority()),
                                t.openedByName(),
                                SupportFormat.dateTime(t.firstResponseDueAt()),
                                platformLink(t.id())));
    }

    public void clinicReplied(TicketRow t) {
        toSupport(t.id(), "Nova mensagem no chamado %s: %s".formatted(t.protocol(), t.subject()), """
                A %s respondeu no chamado %s.

                Abra o chamado: %s
                """.formatted(
                        t.clinicName(), t.protocol(), platformLink(t.id())));
    }

    public void slaBreached(JobTicket t) {
        toSupport(t.id(), "Prazo estourado: chamado %s sem primeira resposta".formatted(t.protocol()), """
                O chamado %s (%s) passou do prazo de primeira resposta.

                Abra o chamado: %s
                """.formatted(
                        t.protocol(), t.subject(), platformLink(t.id())));
    }

    public void supportReplied(TicketRow t) {
        toClinic(t.id(), t.openedByEmail(), "Resposta do suporte no chamado %s".formatted(t.protocol()), """
                Olá, %s.

                A equipe de suporte respondeu ao seu chamado %s (%s).

                Veja a resposta: %s
                """.formatted(
                        firstName(t.openedByName()), t.protocol(), t.subject(), clinicLink(t.id())));
    }

    public void resolved(TicketRow t) {
        toClinic(t.id(), t.openedByEmail(), "Chamado %s resolvido".formatted(t.protocol()), """
                Olá, %s.

                O chamado %s (%s) foi marcado como resolvido.
                Se o problema continuar, responda no chamado em até 7 dias que ele é reaberto.
                Conte também como foi o atendimento: %s
                """.formatted(
                        firstName(t.openedByName()), t.protocol(), t.subject(), clinicLink(t.id())));
    }

    public void waitingReminder(JobTicket t) {
        toClinic(t.id(), t.openedByEmail(), "Chamado %s aguardando sua resposta".formatted(t.protocol()), """
                O chamado %s (%s) está aguardando uma resposta sua há 5 dias.
                Se não houver resposta em mais 5 dias, ele será marcado como resolvido.

                Responder: %s
                """.formatted(
                        t.protocol(), t.subject(), clinicLink(t.id())));
    }

    public void autoResolved(JobTicket t) {
        toClinic(
                t.id(),
                t.openedByEmail(),
                "Chamado %s resolvido por falta de resposta".formatted(t.protocol()),
                """
                O chamado %s (%s) foi marcado como resolvido porque ficou 10 dias sem resposta.
                Se ainda precisar de ajuda, responda no chamado em até 7 dias que ele é reaberto.

                %s
                """.formatted(t.protocol(), t.subject(), clinicLink(t.id())));
    }

    public int dispatch() {
        int sent = 0;
        for (PendingNotification n : repository.pendingNotifications(50)) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(n.recipient());
            message.setSubject(n.subject());
            message.setText(n.body());
            try {
                mail.send(message);
                repository.markNotificationSent(n.id());
                sent++;
            } catch (MailException e) {
                boolean giveUp = n.attempts() + 1 >= MAX_ATTEMPTS;
                repository.markNotificationFailed(n.id(), e.getClass().getSimpleName(), giveUp);
                log.warn(
                        "Falha ao enviar aviso do suporte (tentativa {} de {}): {}",
                        n.attempts() + 1,
                        MAX_ATTEMPTS,
                        e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private void toSupport(UUID ticketId, String subject, String body) {
        Set<String> recipients = new LinkedHashSet<>(repository.platformAdminEmails());
        recipients.addAll(extraRecipients);
        for (String recipient : recipients) {
            repository.insertNotification(Ids.newId(), ticketId, recipient, subject, body + footer());
        }
    }

    private void toClinic(UUID ticketId, String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) return;
        repository.insertNotification(Ids.newId(), ticketId, recipient, subject, body + footer());
    }

    private String footer() {
        return "\n--\nCentral de suporte Odonto SaaS. Atendimento: " + SupportHours.LABEL + ".\n";
    }

    private String clinicLink(UUID id) {
        return publicUrl + "/suporte/" + id;
    }

    private String platformLink(UUID id) {
        return publicUrl + "/plataforma/suporte/" + id;
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.strip().split("\\s+")[0];
    }

    static String priorityLabel(String priority) {
        return switch (priority) {
            case "URGENT" -> "Urgente";
            case "HIGH" -> "Alta";
            case "NORMAL" -> "Normal";
            default -> "Baixa";
        };
    }

    static String categoryLabel(String category) {
        return switch (category) {
            case "BUG" -> "Erro no sistema";
            case "HOW_TO" -> "Dúvida de uso";
            case "ACCESS" -> "Acesso e senha";
            case "BILLING" -> "Assinatura e pagamento";
            case "SUGGESTION" -> "Sugestão";
            default -> "Dados e LGPD";
        };
    }
}
