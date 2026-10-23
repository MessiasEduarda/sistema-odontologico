package br.com.odontosaas.flow;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Regressões da rodada de correções do QA E2E: contato do portal com campos omitidos, paginação extrema e convite do
 * portal fora da exportação e da fila de mensagens.
 */
class SecurityFixesRegressionTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
    }

    @Test
    void portalContactUpdateKeepsFieldsThatWereOmitted() {
        String patient = createPatient(owner, "Contato Parcial");
        Browser portal = portalPatient(owner, patient);

        MvcTestResult update = portal.post("/api/v1/public/portal/contact", "{\"phone\":\"1133334444\"}");
        assertThat(update).hasStatusOk();

        MvcTestResult after = owner.get("/api/v1/patients/" + patient);
        assertThat((String) json(after, "$.phone")).isEqualTo("1133334444");
        assertThat((String) json(after, "$.mobile")).isEqualTo("11999990000");
        assertThat((String) json(after, "$.email")).startsWith("paciente");
        assertThat((Boolean) json(after, "$.allowEmail")).isTrue();
    }

    @Test
    void portalContactUpdateKeepsTheRequiredMobileButChangesConsent() {
        String patient = createPatient(owner, "Contato Total");
        Browser portal = portalPatient(owner, patient);

        MvcTestResult update =
                portal.post("/api/v1/public/portal/contact", "{\"mobile\":\"\",\"phone\":\"\",\"allowEmail\":false}");
        assertThat(update).hasStatusOk();

        MvcTestResult after = owner.get("/api/v1/patients/" + patient);
        assertThat((String) json(after, "$.mobile")).isEqualTo("11999990000");
        assertThat((Object) json(after, "$.phone")).isNull();
        assertThat((Boolean) json(after, "$.allowEmail")).isFalse();
        assertThat((String) json(after, "$.email")).startsWith("paciente");
    }

    @Test
    void extremePageNumberReturnsAnEmptyPageInsteadOfAServerError() {
        MvcTestResult result = owner.get("/api/v1/patients?page=2147483647&size=100");
        assertThat(result).hasStatusOk();
        assertThat((Integer) json(result, "$.items.length()")).isZero();
    }

    @Test
    void exportNeverContainsThePortalInviteToken() {
        String patient = createPatient(owner, "Export Sem Token");
        String token = portalActivationToken(json(owner.get("/api/v1/patients/" + patient), "$.email"));

        MvcTestResult export = owner.get("/api/v1/patients/" + patient + "/export");
        assertThat(export).hasStatusOk();
        assertThat(Browser.body(export)).doesNotContain(token);
        assertThat((Integer) superuserJdbc()
                        .queryForObject(
                                "select count(*) from outbound_messages where body like ?",
                                Integer.class,
                                "%" + token + "%"))
                .isZero();
    }
}
