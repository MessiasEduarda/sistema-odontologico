package br.com.odontosaas.identity.service;

import br.com.odontosaas.identity.config.AppProperties;
import br.com.odontosaas.shared.mail.MailDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
public class IdentityMailer {

    private static final Logger log = LoggerFactory.getLogger(IdentityMailer.class);

    private final MailDelivery sender;
    private final AppProperties app;

    public IdentityMailer(MailDelivery sender, AppProperties app) {
        this.sender = sender;
        this.app = app;
    }

    public void sendPasswordReset(String to, String name, String rawToken) {
        send(to, "Redefinição de senha", """
                Olá, %s.

                Recebemos um pedido para redefinir a sua senha. Para criar uma nova senha, acesse:
                %s/redefinir-senha#token=%s

                O link expira em breve e só pode ser usado uma vez.
                Se você não fez esse pedido, ignore este e-mail — sua senha atual continua valendo.
                """.formatted(name, app.publicUrl(), rawToken));
    }

    public void sendInvitation(String to, String clinicName, String roleName, String rawToken) {
        send(to, "Convite para " + clinicName, """
                Olá!

                Você foi convidado(a) para acessar a clínica %s como %s.
                Para aceitar o convite, acesse:
                %s/convite#token=%s

                Se você não esperava este convite, ignore este e-mail.
                """.formatted(clinicName, roleName, app.publicUrl(), rawToken));
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(app.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("Falha ao enviar e-mail '{}': {}", subject, e.getClass().getSimpleName());
        }
    }
}
