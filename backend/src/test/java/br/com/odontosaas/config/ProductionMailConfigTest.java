package br.com.odontosaas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Envio real de e-mail com a configuração de produção (application.yml + application-prod.yml) contra um servidor
 * SMTP que, como os provedores reais, exige autenticação e STARTTLS (Mailpit configurado para isso).
 */
class ProductionMailConfigTest {

    private static final GenericContainer<?> SMTP = new GenericContainer<>("axllent/mailpit:v1.31.5")
            .withEnv("MP_SMTP_AUTH_ACCEPT_ANY", "true")
            .withEnv("MP_SMTP_REQUIRE_STARTTLS", "true")
            .withEnv("MP_SMTP_TLS_CERT", "sans:localhost")
            .withEnv("MP_SMTP_TLS_KEY", "sans:localhost")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    @BeforeAll
    static void start() {
        SMTP.start();
    }

    @AfterAll
    static void stop() {
        SMTP.stop();
    }

    private static List<PropertySource<?>> yaml(String file) {
        try {
            return new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Contexto só com o envio de e-mail, lendo os arquivos de configuração reais (perfil de produção por cima). */
    private static ApplicationContextRunner mailContext(boolean production) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    if (production) yaml("application-prod.yml").forEach(sources::addLast);
                    yaml("application.yml").forEach(sources::addLast);
                })
                .withPropertyValues(
                        "SMTP_HOST=" + SMTP.getHost(),
                        "SMTP_PORT=" + SMTP.getMappedPort(1025),
                        "SMTP_USERNAME=usuario-do-provedor",
                        "SMTP_PASSWORD=chave-do-provedor",
                        // Certificado autoassinado do servidor de teste; provedores reais têm certificado válido.
                        "spring.mail.properties.mail.smtp.ssl.trust=*");
    }

    private static SimpleMailMessage message(String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("nao-responda@odontosaas.example");
        message.setTo("dono@clinica.example");
        message.setSubject(subject);
        message.setText("Convite de teste com acentuação: você, clínica, ação.");
        return message;
    }

    private static String inbox() throws Exception {
        URI uri = URI.create("http://" + SMTP.getHost() + ":" + SMTP.getMappedPort(8025) + "/api/v1/messages");
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
                    .body();
        }
    }

    @Test
    void productionConfigAuthenticatesWithTlsAndDelivers() {
        mailContext(true).run(context -> {
            assertThat(context).hasNotFailed();
            JavaMailSender sender = context.getBean(JavaMailSender.class);
            sender.send(message("Convite para Clínica Teste"));
            assertThat(inbox()).contains("Convite para Clínica Teste");
        });
    }

    @Test
    void productionRequiresAuthenticationAndEncryptionDevelopmentDoesNot() {
        mailContext(true).run(context -> {
            var props = ((JavaMailSenderImpl) context.getBean(JavaMailSender.class)).getJavaMailProperties();
            assertThat(props)
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.starttls.required", "true");
        });
        // Desenvolvimento (Mailpit local): sem senha e sem criptografia.
        mailContext(false).run(context -> {
            var props = ((JavaMailSenderImpl) context.getBean(JavaMailSender.class)).getJavaMailProperties();
            assertThat(props)
                    .containsEntry("mail.smtp.auth", "false")
                    .containsEntry("mail.smtp.starttls.required", "false");
        });
    }

    @Test
    void productionFailsFastWithoutSmtpSettings() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    yaml("application-prod.yml").forEach(sources::addLast);
                    yaml("application.yml").forEach(sources::addLast);
                })
                .run(context -> assertThat(context).hasFailed());
    }
}
