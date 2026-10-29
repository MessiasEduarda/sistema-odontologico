package br.com.odontosaas.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailDeliveryTest {

    private static final String TEXT = """
            Olá, Diego.

            O chamado SUP-2026-00019 (Agenda <não> abre) foi marcado como resolvido.
            Conte também como foi o atendimento: http://localhost:3000/suporte/abc

            --
            Central de suporte Odonto SaaS.
            """;

    @Test
    void sendsPlainTextAndBrandedHtmlWithTheLogo() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("nao-responda@odontosaas.example");
        message.setTo("dono@exemplo2.example");
        message.setSubject("Chamado SUP-2026-00019 resolvido");
        message.setText(TEXT);
        new MailDelivery(sender).send(message);

        verify(sender).send(mime);
        mime.saveChanges();
        assertThat(mime.getSubject()).isEqualTo("Chamado SUP-2026-00019 resolvido");
        List<BodyPart> parts = new ArrayList<>();
        collect(mime.getContent(), parts);
        assertThat(parts).anySatisfy(p -> {
            assertThat(p.isMimeType("text/plain")).isTrue();
            assertThat(p.getContent()).isEqualTo(TEXT);
        });
        assertThat(parts).anySatisfy(p -> {
            assertThat(p.isMimeType("text/html")).isTrue();
            assertThat((String) p.getContent()).contains("cid:" + EmailLayout.LOGO_ID);
        });
        assertThat(parts).anySatisfy(p -> assertThat(p.isMimeType("image/png")).isTrue());
    }

    @Test
    void htmlTurnsLinksIntoButtonsEscapesTextAndMovesTheSignatureToTheFooter() {
        String html = EmailLayout.html("Chamado SUP-2026-00019 resolvido", TEXT);

        assertThat(html)
                .contains("href=\"http://localhost:3000/suporte/abc\"")
                .contains(">Ver chamado</a>")
                .contains("Agenda &lt;não&gt; abre")
                .doesNotContain("<não>")
                .contains("Central de suporte Odonto SaaS.")
                .doesNotContain("\n--\n");
        assertThat(html.indexOf("Central de suporte")).isGreaterThan(html.indexOf("Ver chamado"));
    }

    @Test
    void htmlHighlightsVerificationCodesAndBuildsLists() {
        String html = EmailLayout.html("Código", """
                Seu código de verificação é: 482913

                - primeiro item
                - segundo item
                """);

        assertThat(html).contains("letter-spacing:0.3em;color:#1E1C1A;\">482913</span>");
        assertThat(html).contains("<li style=\"margin:0 0 4px;\">primeiro item</li>");
    }

    @Test
    void buttonLabelFollowsTheLinkDestination() {
        assertThat(EmailLayout.buttonLabel("http://x/redefinir-senha#token=1")).isEqualTo("Criar nova senha");
        assertThat(EmailLayout.buttonLabel("http://x/convite#token=1")).isEqualTo("Aceitar convite");
        assertThat(EmailLayout.buttonLabel("http://x/portal/ativar#token=1")).isEqualTo("Criar minha senha");
        assertThat(EmailLayout.buttonLabel("http://x/consulta#token=1")).isEqualTo("Ver minha consulta");
        assertThat(EmailLayout.buttonLabel("http://x/termo#token=1")).isEqualTo("Ver e assinar o termo");
        assertThat(EmailLayout.buttonLabel("http://x/plataforma/suporte/1")).isEqualTo("Ver chamado");
        assertThat(EmailLayout.buttonLabel("http://x/outra")).isEqualTo("Abrir");
    }

    private static void collect(Object content, List<BodyPart> parts) throws Exception {
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                parts.add(part);
                collect(part.getContent(), parts);
            }
        }
    }
}
