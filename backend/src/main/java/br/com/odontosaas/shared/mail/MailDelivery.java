package br.com.odontosaas.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envia os e-mails do sistema com duas versões: o texto simples escrito pelo módulo e o HTML com a identidade
 * visual ({@link EmailLayout}). Programas de e-mail que não mostram HTML exibem o texto.
 */
@Component
public class MailDelivery {

    private static final ClassPathResource LOGO = new ClassPathResource("mail/logo.png");

    private final JavaMailSender sender;

    public MailDelivery(JavaMailSender sender) {
        this.sender = sender;
    }

    public void send(SimpleMailMessage message) throws MailException {
        String subject = message.getSubject() == null ? "" : message.getSubject();
        String text = message.getText() == null ? "" : message.getText();
        MimeMessage mime = sender.createMimeMessage();
        try {
            MimeMessageHelper helper =
                    new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            if (message.getFrom() != null) helper.setFrom(message.getFrom());
            if (message.getTo() != null) helper.setTo(message.getTo());
            helper.setSubject(subject);
            helper.setText(text, EmailLayout.html(subject, text));
            helper.addInline(EmailLayout.LOGO_ID, LOGO, "image/png");
        } catch (MessagingException e) {
            throw new MailPreparationException("Não foi possível montar o e-mail", e);
        }
        sender.send(mime);
    }
}
