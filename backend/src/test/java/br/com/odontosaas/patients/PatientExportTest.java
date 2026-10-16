package br.com.odontosaas.patients;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createAppointment;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static br.com.odontosaas.support.Fixtures.createProfessional;
import static br.com.odontosaas.support.Fixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PatientExportTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    @Test
    void exportsEverythingAboutThePatientWithoutSecretsAndIsAudited() throws Exception {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        String patient = createPatient(owner, "Paciente Exportado");
        String professional = createProfessional(owner, "Dra. Export");
        createAppointment(owner, patient, professional, slot(9));

        MvcTestResult export = owner.get("/api/v1/patients/" + patient + "/export");
        assertThat(export).hasStatusOk();
        assertThat((String) json(export, "$.paciente.full_name")).isEqualTo("Paciente Exportado");
        assertThat((List<?>) json(export, "$.consultas")).hasSize(1);
        assertThat((List<?>) json(export, "$.anamneses")).isEmpty();
        Map<String, Object> appointment = json(export, "$.consultas[0]");
        assertThat(appointment).doesNotContainKeys("tenant_id", "confirmation_token_hash");
        assertThat(export.getResponse().getContentAsString()).doesNotContain("token_hash");

        Integer audited = superuserJdbc()
                .queryForObject(
                        "select count(*) from odonto.audit_events where action = 'PATIENT_DATA_EXPORTED'"
                                + " and resource_id = ?",
                        Integer.class,
                        patient);
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void requiresClinicalAccessAndStaysInsideTheClinic() {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        String patient = createPatient(owner, "Paciente Protegido");

        Browser reception = browser().login(data.member(clinic, "RECEPTION").email(), TestData.PASSWORD);
        assertThat(reception.get("/api/v1/patients/" + patient + "/export")).hasStatus(HttpStatus.FORBIDDEN);

        Browser otherOwner = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(otherOwner.get("/api/v1/patients/" + patient + "/export")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
