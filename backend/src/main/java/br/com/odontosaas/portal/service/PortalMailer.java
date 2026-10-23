package br.com.odontosaas.portal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PortalMailer {

    private static final Logger log = LoggerFactory.getLogger(PortalMailer.class);

    private final JavaMailSender sender;
    private final String from;
    private final String publicUrl;

    public PortalMailer(
            JavaMailSender sender,
            @Value("${app.mail.from}") String from,
            @Value("${app.public-url}") String publicUrl) {
        this.sender = sender;
        this.from = from;
        this.publicUrl = publicUrl;
    }

    public void sendActivation(String to, String firstName, String clinicName, String rawToken) {
        afterCommit(to, "Crie sua senha do portal do paciente — " + clinicName, """
                Olá, %s!

                A %s criou o seu acesso ao portal do paciente. Nele você acompanha suas consultas, assina termos e
                baixa receitas, atestados, exames e recibos liberados pela clínica.

                Para criar sua senha, acesse (o link vale por 48 horas e só pode ser usado uma vez):
                %s/portal/ativar#token=%s

                Por segurança, vamos pedir a sua data de nascimento antes de criar a senha.
                Se você não é paciente da %s, ignore este e-mail.
                """.formatted(
                        firstName, clinicName, publicUrl, rawToken, clinicName));
    }

    public void sendNewClinicAccess(String to, String firstName, String clinicName) {
        afterCommit(to, "Novo acesso no portal do paciente — " + clinicName, """
                Olá, %s!

                A %s liberou o seu acesso no portal do paciente. Entre com o e-mail e a senha que você já usa:
                %s/portal/entrar
                """.formatted(
                        firstName, clinicName, publicUrl));
    }

    public void sendLoginCode(String to, String code) {
        send(to, "Seu código de acesso ao portal do paciente", """
                Seu código de verificação é: %s

                Ele vale por 10 minutos. Pedimos o código porque é o primeiro acesso neste aparelho.
                Se não foi você, ignore este e-mail e troque a sua senha.
                """.formatted(code));
    }

    public void sendPasswordReset(String to, String rawToken) {
        send(to, "Redefinição de senha do portal do paciente", """
                Recebemos um pedido para redefinir a senha do seu acesso ao portal do paciente.
                Para criar uma nova senha, acesse (o link vale por 1 hora e só pode ser usado uma vez):
                %s/portal/redefinir-senha#token=%s

                Se você não fez esse pedido, ignore este e-mail — sua senha atual continua valendo.
                """.formatted(publicUrl, rawToken));
    }

    private void afterCommit(String to, String subject, String body) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(to, subject, body);
                }
            });
        } else {
            send(to, subject, body);
        }
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error(
                    "Falha ao enviar e-mail do portal '{}': {}",
                    subject,
                    e.getClass().getSimpleName());
        }
    }
}
