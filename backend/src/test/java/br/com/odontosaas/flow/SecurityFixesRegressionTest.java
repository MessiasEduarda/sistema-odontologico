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
 * Regressões da rodada de correções do QA E2E: contato do portal com campos omitidos, paginação extrema e links de
 * acesso do paciente fora da exportação.
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

    private String portalToken(String patient, boolean sendEmail) {
        MvcTestResult grant =
                owner.post("/api/v1/patients/" + patient + "/portal-access", "{\"sendEmail\":%s}".formatted(sendEmail));
        assertThat(grant).hasStatusOk();
        String url = json(grant, "$.url");
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    @Test
    void portalContactUpdateKeepsFieldsThatWereOmitted() {
        String patient = createPatient(owner, "Contato Parcial");
        String token = portalToken(patient, false);

        MvcTestResult update = browser()
                .post("/api/v1/public/portal/contact", "{\"token\":\"%s\",\"phone\":\"1133334444\"}".formatted(token));
        assertThat(update).hasStatusOk();

        MvcTestResult after = owner.get("/api/v1/patients/" + patient);
        assertThat((String) json(after, "$.phone")).isEqualTo("1133334444");
        assertThat((String) json(after, "$.mobile")).isEqualTo("11999990000");
        assertThat((String) json(after, "$.email")).startsWith("paciente");
        assertThat((Boolean) json(after, "$.allowEmail")).isTrue();
    }

    @Test
    void portalContactUpdateStillAllowsClearingAndChangingConsent() {
        String patient = createPatient(owner, "Contato Total");
        String token = portalToken(patient, false);

        MvcTestResult update = browser()
                .post(
                        "/api/v1/public/portal/contact",
                        "{\"token\":\"%s\",\"mobile\":\"\",\"allowEmail\":false}".formatted(token));
        assertThat(update).hasStatusOk();

        MvcTestResult after = owner.get("/api/v1/patients/" + patient);
        assertThat((Object) json(after, "$.mobile")).isNull();
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
    void exportMasksPatientAccessLinksStoredInQueuedMessages() {
        String patient = createPatient(owner, "Export Mascarado");
        String token = portalToken(patient, true);

        MvcTestResult export = owner.get("/api/v1/patients/" + patient + "/export");
        assertThat(export).hasStatusOk();
        String body = Browser.body(export);
        assertThat(body).contains("#token=[removido]").doesNotContain(token);
    }
}
