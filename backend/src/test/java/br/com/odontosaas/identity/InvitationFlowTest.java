package br.com.odontosaas.identity;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Browser.loginJson;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import br.com.odontosaas.support.TestData.Person;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class InvitationFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    private String invite(Browser owner, String email, String role) {
        MvcTestResult result = owner.post(
                "/api/v1/clinic/invitations", "{\"email\":\"%s\",\"roleCode\":\"%s\"}".formatted(email, role));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return tokenFromLastMail();
    }

    @Test
    void newPersonAcceptsInvitationAndGetsTheInvitedRole() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        String email = "nova" + TestData.unique() + "@teste.example";
        String token = invite(browser().login(owner.email(), TestData.PASSWORD), email, "DENTIST");

        MvcTestResult preview = browser().post("/api/v1/public/invitations/preview", "{\"token\":\"" + token + "\"}");
        assertThat(preview).hasStatusOk();
        assertThat((String) json(preview, "$.email")).isEqualTo(email);
        assertThat((Boolean) json(preview, "$.existingAccount")).isFalse();

        assertThat(browser()
                        .post(
                                "/api/v1/public/invitations/accept",
                                "{\"token\":\"%s\",\"name\":\"Nova Pessoa\",\"password\":\"%s\"}"
                                        .formatted(token, "Senha-Nova-Forte-1")))
                .hasStatus(HttpStatus.NO_CONTENT);

        MvcTestResult login = browser().post("/api/v1/auth/login", loginJson(email, "Senha-Nova-Forte-1"));
        assertThat(login).hasStatusOk();
        assertThat((String) json(login, "$.activeClinic.id")).isEqualTo(clinic.toString());
        List<String> permissions = json(login, "$.permissions");
        assertThat(permissions).contains("clinical_records:write").doesNotContain("users:manage");
    }

    @Test
    void invitationTokenWorksOnlyOnce() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        String token = invite(
                browser().login(owner.email(), TestData.PASSWORD),
                "uma-vez" + TestData.unique() + "@teste.example",
                "RECEPTION");
        String accept = "{\"token\":\"%s\",\"name\":\"Pessoa\",\"password\":\"Senha-Nova-Forte-1\"}".formatted(token);

        assertThat(browser().post("/api/v1/public/invitations/accept", accept)).hasStatus(HttpStatus.NO_CONTENT);
        MvcTestResult again = browser().post("/api/v1/public/invitations/accept", accept);
        assertThat(again).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(again, "$.code")).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void existingAccountMustConfirmItsPassword() {
        UUID clinicA = data.clinic();
        UUID clinicB = data.clinic();
        Person owner = data.member(clinicA, "OWNER");
        Person existing = data.member(clinicB, "DENTIST");
        String token = invite(browser().login(owner.email(), TestData.PASSWORD), existing.email(), "DENTIST");

        assertThat(browser()
                        .post(
                                "/api/v1/public/invitations/accept",
                                "{\"token\":\"%s\",\"password\":\"Senha-Errada-123\"}".formatted(token)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(browser()
                        .post(
                                "/api/v1/public/invitations/accept",
                                "{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, TestData.PASSWORD)))
                .hasStatus(HttpStatus.NO_CONTENT);

        List<String> clinics =
                json(browser().login(existing.email(), TestData.PASSWORD).get("/api/v1/auth/me"), "$.clinics[*].id");
        assertThat(clinics).containsExactlyInAnyOrder(clinicA.toString(), clinicB.toString());
    }

    @Test
    void weakPasswordIsRejectedOnAccept() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        String token = invite(
                browser().login(owner.email(), TestData.PASSWORD),
                "fraca" + TestData.unique() + "@teste.example",
                "RECEPTION");

        MvcTestResult result = browser()
                .post(
                        "/api/v1/public/invitations/accept",
                        "{\"token\":\"%s\",\"name\":\"Pessoa\",\"password\":\"1234567890\"}".formatted(token));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(result, "$.fieldErrors[0].field")).isEqualTo("password");
    }

    @Test
    void cannotInviteSomeoneWhoIsAlreadyAMember() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        Person dentist = data.member(clinic, "DENTIST");

        MvcTestResult result = browser()
                .login(owner.email(), TestData.PASSWORD)
                .post(
                        "/api/v1/clinic/invitations",
                        "{\"email\":\"%s\",\"roleCode\":\"DENTIST\"}".formatted(dentist.email()));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(result, "$.code")).isEqualTo("ALREADY_MEMBER");
    }

    @Test
    void garbageTokensAreRejected() {
        assertThat(browser().post("/api/v1/public/invitations/preview", "{\"token\":\"" + "x".repeat(43) + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(browser().post("/api/v1/public/invitations/preview", "{\"token\":\"' or 1=1 --\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invitationTokenIsStoredOnlyAsHash() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        String token = invite(
                browser().login(owner.email(), TestData.PASSWORD),
                "hash" + TestData.unique() + "@teste.example",
                "RECEPTION");

        Integer plain = superuserJdbc()
                .queryForObject("select count(*) from invitations where token_hash = ?", Integer.class, token);
        assertThat(plain).isZero();
    }
}
