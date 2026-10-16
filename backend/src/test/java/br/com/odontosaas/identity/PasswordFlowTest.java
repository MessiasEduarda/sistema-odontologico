package br.com.odontosaas.identity;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Browser.loginJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import br.com.odontosaas.support.TestData.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PasswordFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    private static String forgot(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    private static String reset(String token, String password) {
        return "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password);
    }

    @Test
    void unknownEmailGetsSameAnswerAndNoEmailIsSent() {
        assertThat(browser()
                        .post("/api/v1/auth/password/forgot", forgot("ninguem" + TestData.unique() + "@teste.example")))
                .hasStatus(HttpStatus.ACCEPTED);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetChangesPasswordAndEndsAllSessions() {
        Person person = data.user();
        Browser oldSession = browser().login(person.email(), TestData.PASSWORD);

        assertThat(browser().post("/api/v1/auth/password/forgot", forgot(person.email())))
                .hasStatus(HttpStatus.ACCEPTED);
        String token = tokenFromLastMail();

        assertThat(browser().post("/api/v1/auth/password/reset", reset(token, "Nova-Senha-Segura-9")))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(oldSession.get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(browser().post("/api/v1/auth/login", loginJson(person.email(), TestData.PASSWORD)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(browser().post("/api/v1/auth/login", loginJson(person.email(), "Nova-Senha-Segura-9")))
                .hasStatusOk();

        MvcTestResult reuse = browser().post("/api/v1/auth/password/reset", reset(token, "Outra-Senha-Segura-9"));
        assertThat(reuse).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(reuse, "$.code")).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void resetRejectsWeakPasswordAndKeepsTokenUsable() {
        Person person = data.user();
        browser().post("/api/v1/auth/password/forgot", forgot(person.email()));
        String token = tokenFromLastMail();

        MvcTestResult weak = browser().post("/api/v1/auth/password/reset", reset(token, "curta"));
        assertThat(weak).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(weak, "$.fieldErrors[0].field")).isEqualTo("newPassword");

        assertThat(browser().post("/api/v1/auth/password/reset", reset(token, "Nova-Senha-Segura-9")))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void expiredTokenIsRejected() {
        Person person = data.user();
        browser().post("/api/v1/auth/password/forgot", forgot(person.email()));
        String token = tokenFromLastMail();
        superuserJdbc()
                .update(
                        "update password_reset_tokens set expires_at = now() - interval '1 minute' where user_id = ?",
                        person.id());

        assertThat(browser().post("/api/v1/auth/password/reset", reset(token, "Nova-Senha-Segura-9")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changePasswordRequiresCurrentPasswordAndKeepsOnlyThisSession() {
        Person person = data.user();
        Browser laptop = browser().login(person.email(), TestData.PASSWORD);
        Browser phone = browser().login(person.email(), TestData.PASSWORD);

        assertThat(laptop.post(
                        "/api/v1/auth/password/change",
                        "{\"currentPassword\":\"errada-errada\",\"newPassword\":\"Nova-Senha-Segura-9\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(laptop.post(
                        "/api/v1/auth/password/change",
                        "{\"currentPassword\":\"%s\",\"newPassword\":\"Nova-Senha-Segura-9\"}"
                                .formatted(TestData.PASSWORD)))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(laptop.get("/api/v1/auth/me")).hasStatusOk();
        assertThat(phone.get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordsAreStoredWithArgon2() {
        Person person = data.user();
        String hash = superuserJdbc()
                .queryForObject("select password_hash from users where id = ?", String.class, person.id());
        assertThat(hash).startsWith("$argon2id$").doesNotContain(TestData.PASSWORD);
    }
}
