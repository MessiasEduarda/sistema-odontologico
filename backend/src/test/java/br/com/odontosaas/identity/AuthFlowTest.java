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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AuthFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void loginSetsHttpOnlyCookieAndSelectsTheOnlyClinic() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        Browser browser = browser();

        MvcTestResult result = browser.post("/api/v1/auth/login", loginJson(owner.email(), TestData.PASSWORD));

        assertThat(result).hasStatusOk();
        String setCookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(Browser.SESSION_COOKIE + "="))
                .findFirst()
                .orElseThrow();
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Secure");
        assertThat((String) json(result, "$.activeClinic.id")).isEqualTo(clinic.toString());
        assertThat((List<String>) json(result, "$.permissions")).contains("users:manage", "patients:read");

        assertThat(browser.get("/api/v1/auth/me")).hasStatusOk();
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() {
        Person person = data.user();
        MvcTestResult wrongPassword =
                browser().post("/api/v1/auth/login", loginJson(person.email(), "Senha-Errada-999"));
        MvcTestResult unknownEmail = browser()
                .post("/api/v1/auth/login", loginJson("nao-existe-" + TestData.unique() + "@teste.example", "x"));

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat((String) json(wrongPassword, "$.detail")).isEqualTo(json(unknownEmail, "$.detail"));
        assertThat((String) json(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void emailIsCaseInsensitive() {
        Person person = data.user();
        assertThat(browser().post("/api/v1/auth/login", loginJson(person.email().toUpperCase(), TestData.PASSWORD)))
                .hasStatusOk();
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsRejected() {
        Person person = data.user();
        MvcTestResult result =
                browser().postWithoutCsrf("/api/v1/auth/login", loginJson(person.email(), TestData.PASSWORD));
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(result, "$.code")).isEqualTo("CSRF_INVALID");
    }

    @Test
    void csrfEndpointIssuesReadableCookie() {
        MvcTestResult result = mvc.get().uri("/api/v1/auth/csrf").exchange(); // sem cookie prévio
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(String.join(";", result.getResponse().getHeaders("Set-Cookie")))
                .contains("XSRF-TOKEN=");
    }

    @Test
    void logoutRevokesTheSessionOnTheServer() {
        Person person = data.user();
        Browser browser = browser().login(person.email(), TestData.PASSWORD);
        String stolenCopy = browser.session();

        assertThat(browser.post("/api/v1/auth/logout", null)).hasStatus(HttpStatus.NO_CONTENT);

        // Mesmo quem guardou uma cópia do cookie não consegue mais usá-lo.
        assertThat(browser().useSession(stolenCopy).get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void accountIsTemporarilyBlockedAfterRepeatedFailures() {
        Person person = data.user();
        for (int i = 0; i < 5; i++) {
            assertThat(browser().post("/api/v1/auth/login", loginJson(person.email(), "Senha-Errada-" + i)))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
        MvcTestResult blocked = browser().post("/api/v1/auth/login", loginJson(person.email(), TestData.PASSWORD));

        assertThat(blocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasHeader("Retry-After", "900");
        assertThat((String) json(blocked, "$.code")).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void idleSessionExpires() {
        Person person = data.user();
        Browser browser = browser().login(person.email(), TestData.PASSWORD);
        jdbc.update("update sessions set last_seen_at = now() - interval '31 minutes' where user_id = ?", person.id());

        assertThat(browser.get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void disabledUserLosesAccessImmediately() {
        Person person = data.user();
        Browser browser = browser().login(person.email(), TestData.PASSWORD);
        jdbc.update("update users set status = 'DISABLED' where id = ?", person.id());

        assertThat(browser.get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(browser().post("/api/v1/auth/login", loginJson(person.email(), TestData.PASSWORD)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgedOrMalformedSessionCookieIsIgnored() {
        assertThat(browser().useSession("a".repeat(43)).get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(browser().useSession("'; drop table users; --").get("/api/v1/auth/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userCanListAndRevokeOwnSessions() {
        Person person = data.user();
        Browser laptop = browser().login(person.email(), TestData.PASSWORD);
        Browser phone = browser().login(person.email(), TestData.PASSWORD);

        MvcTestResult list = laptop.get("/api/v1/auth/sessions");
        assertThat(list).hasStatusOk();
        assertThat((List<?>) json(list, "$")).hasSize(2);

        assertThat(laptop.post("/api/v1/auth/sessions/revoke-others", null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(phone.get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(laptop.get("/api/v1/auth/me")).hasStatusOk();
    }

    @Test
    void userCannotRevokeSomeoneElsesSession() {
        Person alice = data.user();
        Person bob = data.user();
        browser().login(bob.email(), TestData.PASSWORD);
        UUID bobSession = jdbc.queryForObject(
                "select id from sessions where user_id = ? and revoked_at is null limit 1", UUID.class, bob.id());

        Browser attacker = browser().login(alice.email(), TestData.PASSWORD);
        assertThat(attacker.delete("/api/v1/auth/sessions/" + bobSession)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void loginAttemptsAreAudited() {
        Person person = data.user();
        browser().post("/api/v1/auth/login", loginJson(person.email(), "Senha-Errada-1"));
        browser().login(person.email(), TestData.PASSWORD);

        List<String> actions = superuserJdbc()
                .queryForList(
                        "select action from audit_events where actor_user_id = ? order by occurred_at",
                        String.class,
                        person.id());
        assertThat(actions).containsExactly("LOGIN_FAILED", "LOGIN_SUCCEEDED");
    }
}
