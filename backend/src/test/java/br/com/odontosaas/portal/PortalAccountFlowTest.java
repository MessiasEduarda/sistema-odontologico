package br.com.odontosaas.portal;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PortalAccountFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
    }

    private String emailOf(String patient) {
        return json(owner.get("/api/v1/patients/" + patient), "$.email");
    }

    private String lastText(String to) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues().reversed().stream()
                .filter(m -> List.of(m.getTo()).contains(to))
                .findFirst()
                .orElseThrow()
                .getText();
    }

    private static String match(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) throw new AssertionError("Não encontrado: " + regex);
        return m.group(1);
    }

    private static String activation(String token, String birth, String password) {
        return "{\"token\":\"%s\",\"birthDate\":\"%s\",\"password\":\"%s\",\"acceptedTerms\":true}"
                .formatted(token, birth, password);
    }

    @Test
    void registeringThePatientSendsTheInviteAndActivationChecksTheBirthDate() {
        String patient = createPatient(owner, "Helena Convite");
        String email = emailOf(patient);
        assertThat((String) json(owner.get("/api/v1/patients/" + patient + "/portal-account"), "$.status"))
                .isEqualTo("INVITED");
        String token = portalActivationToken(email);

        MvcTestResult lookup =
                browser().post("/api/v1/public/portal/activation/lookup", "{\"token\":\"" + token + "\"}");
        assertThat((String) json(lookup, "$.firstName")).isEqualTo("Helena");
        assertThat((String) json(lookup, "$.email")).isEqualTo(email);

        Browser patientBrowser = browser();
        MvcTestResult wrongBirth = patientBrowser.post(
                "/api/v1/public/portal/activation", activation(token, "2000-01-01", TestData.PASSWORD));
        assertThat(wrongBirth).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(wrongBirth, "$.fieldErrors[0].field")).isEqualTo("birthDate");

        assertThat(patientBrowser.post("/api/v1/public/portal/activation", activation(token, "1990-05-10", "123")))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(patientBrowser.post(
                        "/api/v1/public/portal/activation", activation(token, "1990-05-10", TestData.PASSWORD)))
                .hasStatusOk();
        assertThat(patientBrowser.cookie("odonto_portal")).isNotBlank();
        assertThat(patientBrowser.get("/api/v1/public/portal/overview")).hasStatusOk();
        assertThat((String) json(owner.get("/api/v1/patients/" + patient + "/portal-account"), "$.status"))
                .isEqualTo("ACTIVE");

        assertThat(browser()
                        .post("/api/v1/public/portal/activation", activation(token, "1990-05-10", TestData.PASSWORD)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void newDeviceNeedsTheEmailCodeAndWrongCredentialsGetTheSameAnswer() {
        String patient = createPatient(owner, "Bruno Aparelho");
        String email = emailOf(patient);
        portalPatient(owner, patient);
        clearInvocations(mailSender);

        Browser device = browser();
        MvcTestResult first = device.post("/api/v1/public/portal/login", Browser.loginJson(email, TestData.PASSWORD));
        assertThat((String) json(first, "$.status")).isEqualTo("CODE_REQUIRED");
        assertThat(device.cookie("odonto_portal")).isNull();
        String challenge = json(first, "$.challengeId");
        String code = match(lastText(email), "código de verificação é: ([0-9]{6})");

        String wrong = code.equals("000000") ? "111111" : "000000";
        assertThat(device.post(
                        "/api/v1/public/portal/login/verify",
                        "{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challenge, wrong)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        MvcTestResult ok = device.post(
                "/api/v1/public/portal/login/verify",
                "{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challenge, code));
        assertThat((String) json(ok, "$.status")).isEqualTo("AUTHENTICATED");
        assertThat(device.get("/api/v1/public/portal/overview")).hasStatusOk();

        assertThat(device.post(
                        "/api/v1/public/portal/login/verify",
                        "{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challenge, code)))
                .hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult again = device.post("/api/v1/public/portal/login", Browser.loginJson(email, TestData.PASSWORD));
        assertThat((String) json(again, "$.status")).isEqualTo("AUTHENTICATED");

        MvcTestResult badPassword =
                browser().post("/api/v1/public/portal/login", Browser.loginJson(email, "Senha-Errada-123"));
        MvcTestResult unknown = browser()
                .post("/api/v1/public/portal/login", Browser.loginJson("ninguem@teste.example", "Senha-Errada-123"));
        assertThat(badPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknown).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat((String) json(badPassword, "$.detail")).isEqualTo(json(unknown, "$.detail"));
    }

    @Test
    void clinicCanBlockAccessAndEmailChangeMovesTheInvite() {
        String patient = createPatient(owner, "Carla Bloqueio");
        Browser portal = portalPatient(owner, patient);

        assertThat((String) json(owner.post("/api/v1/patients/" + patient + "/portal-account/block", null), "$.status"))
                .isEqualTo("BLOCKED");
        assertThat(portal.get("/api/v1/public/portal/overview")).hasStatus(HttpStatus.UNAUTHORIZED);
        owner.post("/api/v1/patients/" + patient + "/portal-account/unblock", null);

        Browser again = browser().withCookie("odonto_portal_device", portal.cookie("odonto_portal_device"));
        again.post("/api/v1/public/portal/login", Browser.loginJson(emailOf(patient), TestData.PASSWORD));
        assertThat(again.get("/api/v1/public/portal/overview")).hasStatusOk();

        MvcTestResult current = owner.get("/api/v1/patients/" + patient);
        String body =
                Browser.body(current).replace(emailOf(patient), "novo.email" + TestData.unique() + "@teste.example");
        assertThat(owner.put("/api/v1/patients/" + patient, body)).hasStatusOk();
        assertThat(again.get("/api/v1/public/portal/overview")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat((String) json(owner.get("/api/v1/patients/" + patient + "/portal-account"), "$.status"))
                .isEqualTo("INVITED");
        assertThat(portalActivationToken(emailOf(patient))).isNotBlank();
    }

    @Test
    void passwordResetEndsOpenSessions() {
        String patient = createPatient(owner, "Diana Senha");
        String email = emailOf(patient);
        Browser portal = portalPatient(owner, patient);
        clearInvocations(mailSender);

        assertThat(browser().post("/api/v1/public/portal/password/forgot", "{\"email\":\"" + email + "\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(browser().post("/api/v1/public/portal/password/forgot", "{\"email\":\"ninguem@teste.example\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);
        String token = match(lastText(email), "/portal/redefinir-senha#token=([A-Za-z0-9_-]{43})");

        assertThat(browser()
                        .post(
                                "/api/v1/public/portal/password/reset",
                                "{\"token\":\"%s\",\"password\":\"Nova-Senha-Forte-9\"}".formatted(token)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(portal.get("/api/v1/public/portal/overview")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(browser().post("/api/v1/public/portal/login", Browser.loginJson(email, TestData.PASSWORD)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat((String) json(
                        browser().post("/api/v1/public/portal/login", Browser.loginJson(email, "Nova-Senha-Forte-9")),
                        "$.status"))
                .isEqualTo("CODE_REQUIRED");
    }

    @Test
    void patientOnlySeesItsOwnProfileAndNeedsALogin() {
        String ana = createPatient(owner, "Ana Perfil");
        String bia = createPatient(owner, "Bia Perfil");
        Browser anaPortal = portalPatient(owner, ana);
        portalPatient(owner, bia);

        MvcTestResult me = anaPortal.get("/api/v1/public/portal/me");
        assertThat((List<String>) json(me, "$.profiles[*].patientId")).containsExactly(ana);
        assertThat(anaPortal.post(
                        "/api/v1/public/portal/profile",
                        "{\"tenantId\":\"%s\",\"patientId\":\"%s\"}".formatted(clinic, bia)))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(browser().get("/api/v1/public/portal/overview")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(browser().withCookie("odonto_portal", "x".repeat(43)).get("/api/v1/public/portal/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(owner.get("/api/v1/public/portal/overview")).hasStatus(HttpStatus.UNAUTHORIZED);
        verify(mailSender, atLeastOnce()).send(any(SimpleMailMessage.class));
    }
}
