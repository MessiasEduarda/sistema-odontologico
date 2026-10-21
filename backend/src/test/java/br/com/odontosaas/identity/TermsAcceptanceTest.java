package br.com.odontosaas.identity;

import static br.com.odontosaas.support.Browser.json;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.identity.service.TermsService;
import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import br.com.odontosaas.support.TestData.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class TermsAcceptanceTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    private static String accept(String version) {
        return "{\"version\":\"" + version + "\"}";
    }

    @Test
    void acceptsOnlyOncePerUserAndIsRecordedWithEvidence() {
        Person person = data.user();
        Browser session = browser().login(person.email(), TestData.PASSWORD);

        var me = session.get("/api/v1/auth/me");
        assertThat(me).hasStatusOk();
        assertThat((Boolean) json(me, "$.termsAccepted")).isFalse();
        assertThat((String) json(me, "$.termsVersion")).isEqualTo(TermsService.CURRENT_VERSION);

        var accepted = session.post("/api/v1/auth/terms/accept", accept(TermsService.CURRENT_VERSION));
        assertThat(accepted).hasStatusOk();
        assertThat((Boolean) json(accepted, "$.termsAccepted")).isTrue();

        // Repetir não duplica nem falha; um novo login continua com o aceite.
        assertThat(session.post("/api/v1/auth/terms/accept", accept(TermsService.CURRENT_VERSION)))
                .hasStatusOk();
        var again = browser().login(person.email(), TestData.PASSWORD).get("/api/v1/auth/me");
        assertThat((Boolean) json(again, "$.termsAccepted")).isTrue();

        var db = superuserJdbc();
        assertThat(db.queryForObject(
                        "select count(*) from odonto.terms_acceptances where user_id = ?", Integer.class, person.id()))
                .isEqualTo(1);
        assertThat(db.queryForObject(
                        "select count(*) from odonto.audit_events where actor_user_id = ? and action = 'TERMS_ACCEPTED'",
                        Integer.class,
                        person.id()))
                .isEqualTo(1);
    }

    @Test
    void refusesOutdatedVersionAndRequiresSession() {
        Person person = data.user();
        Browser session = browser().login(person.email(), TestData.PASSWORD);

        assertThat(session.post("/api/v1/auth/terms/accept", accept("2000-01-01")))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(browser().post("/api/v1/auth/terms/accept", accept(TermsService.CURRENT_VERSION)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void applicationCannotChangeOrDeleteAcceptances() {
        var db = superuserJdbc();
        for (String privilege : new String[] {"UPDATE", "DELETE"}) {
            assertThat(db.queryForObject(
                            "select has_table_privilege(?, 'odonto.terms_acceptances', ?)",
                            Boolean.class,
                            APP_USER,
                            privilege))
                    .as(privilege)
                    .isFalse();
        }
    }
}
