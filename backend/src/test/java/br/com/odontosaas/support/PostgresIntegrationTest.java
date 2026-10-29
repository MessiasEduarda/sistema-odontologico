package br.com.odontosaas.support;

import br.com.odontosaas.shared.mail.MailDelivery;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base dos testes de integração: PostgreSQL real (mesma versão do docker-compose) inicializado pelo MESMO script de
 * bootstrap do ambiente local. A aplicação conecta com o papel restrito {@code odonto_app} e o Flyway com
 * {@code odonto_migrator} — nunca com o superusuário — para que permissões e RLS sejam testados de verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    /** E-mails não são enviados nos testes; o mock permite capturar tokens de convite/redefinição. */
    @MockitoBean
    protected MailDelivery mailSender;

    @Autowired
    protected MockMvcTester mvc;

    public static final String APP_USER = "odonto_app";
    public static final String APP_PASSWORD = "test_app_pw";
    public static final String MIGRATOR_USER = "odonto_migrator";
    public static final String MIGRATOR_PASSWORD = "test_migrator_pw";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("odonto")
            .withEnv("APP_DB_USER", APP_USER)
            .withEnv("APP_DB_PASSWORD", APP_PASSWORD)
            .withEnv("MIGRATOR_DB_USER", MIGRATOR_USER)
            .withEnv("MIGRATOR_DB_PASSWORD", MIGRATOR_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(
                            Path.of("..", "infrastructure", "postgres", "init", "01-bootstrap.sh"), 0755),
                    "/docker-entrypoint-initdb.d/01-bootstrap.sh");

    static {
        POSTGRES.start();
    }

    /**
     * Conexão de SUPERUSUÁRIO (ignora RLS), apenas para os testes conferirem o estado real do banco. A aplicação
     * nunca usa esse papel.
     */
    protected static org.springframework.jdbc.core.JdbcTemplate superuserJdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                                + "currentSchema=odonto",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()));
    }

    protected Browser browser() {
        return new Browser(mvc);
    }

    /** Token (convite ou redefinição) do último e-mail "enviado". */
    protected String tokenFromLastMail() {
        org.mockito.ArgumentCaptor<org.springframework.mail.SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.atLeastOnce())
                .send(captor.capture());
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#token=([A-Za-z0-9_-]{43})")
                .matcher(captor.getValue().getText());
        if (!matcher.find()) throw new AssertionError("E-mail sem token");
        return matcher.group(1);
    }

    /** Token do último convite do portal enviado para o e-mail informado. */
    protected String portalActivationToken(String email) {
        org.mockito.ArgumentCaptor<org.springframework.mail.SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(mailSender, org.mockito.Mockito.atLeastOnce())
                .send(captor.capture());
        String token = null;
        for (org.springframework.mail.SimpleMailMessage message : captor.getAllValues()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "/portal/ativar#token=([A-Za-z0-9_-]{43})")
                    .matcher(message.getText());
            if (java.util.Arrays.asList(message.getTo()).contains(email) && matcher.find()) token = matcher.group(1);
        }
        if (token == null) throw new AssertionError("Nenhum convite do portal para " + email);
        return token;
    }

    /** Ativa a conta do portal do paciente (convite enviado no cadastro) e devolve o navegador já logado. */
    protected Browser portalPatient(Browser clinic, String patientId) {
        String email = Browser.json(clinic.get("/api/v1/patients/" + patientId), "$.email");
        Browser patient = browser();
        org.assertj.core.api.Assertions.assertThat(patient.post(
                        "/api/v1/public/portal/activation",
                        ("{\"token\":\"%s\",\"birthDate\":\"1990-05-10\",\"password\":\"%s\","
                                        + "\"acceptedTerms\":true}")
                                .formatted(portalActivationToken(email), TestData.PASSWORD)))
                .hasStatusOk();
        return patient;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> MIGRATOR_PASSWORD);
    }
}
