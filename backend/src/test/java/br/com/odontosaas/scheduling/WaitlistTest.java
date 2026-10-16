package br.com.odontosaas.scheduling;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class WaitlistTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser reception;
    String professional;
    String patient;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        reception = browser().login(data.member(clinic, "RECEPTION").email(), TestData.PASSWORD);
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        professional = createProfessional(owner, "Dra. Espera");
        patient = createPatient(reception, "Paciente Espera");
    }

    private static String entry(String patientId, String professionalId, boolean urgent) {
        return """
                {"patientId":"%s","professionalId":%s,"procedureId":null,
                 "preference":"Manhãs, terça ou quinta","notes":"Quer adiantar a limpeza","urgent":%s}
                """.formatted(patientId, professionalId == null ? "null" : "\"" + professionalId + "\"", urgent);
    }

    @Test
    void addsSortsAndSchedulesFromTheWaitlist() {
        MvcTestResult created = reception.post("/api/v1/waitlist", entry(patient, professional, false));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = json(created, "$.id");
        assertThat((String) json(created, "$.professionalName")).isEqualTo("Dra. Espera");
        assertThat((String) json(created, "$.status")).isEqualTo("WAITING");

        // O mesmo paciente não entra duas vezes enquanto aguarda.
        assertThat(reception.post("/api/v1/waitlist", entry(patient, null, false)))
                .hasStatus(HttpStatus.CONFLICT);

        // Urgentes ficam no topo, mesmo chegando depois.
        String urgentPatient = createPatient(reception, "Paciente Urgente");
        assertThat(reception.post("/api/v1/waitlist", entry(urgentPatient, null, true)))
                .hasStatus(HttpStatus.CREATED);
        List<String> names = json(reception.get("/api/v1/waitlist"), "$[*].patientName");
        assertThat(names).containsExactly("Paciente Urgente", "Paciente Espera");

        MvcTestResult edited = reception.put("/api/v1/waitlist/" + id, """
                {"patientId":"%s","professionalId":null,"preference":"Qualquer horário","urgent":false}
                """.formatted(patient));
        assertThat(edited).hasStatus(HttpStatus.OK);
        assertThat((String) json(edited, "$.preference")).isEqualTo("Qualquer horário");
        assertThat((Object) json(edited, "$.professionalId")).isNull();

        // Encaixe: só com um atendimento do mesmo paciente.
        String otherAppointment = createAppointment(reception, urgentPatient, professional, slot(2));
        assertThat(reception.post(
                        "/api/v1/waitlist/" + id + "/resolve",
                        "{\"status\":\"SCHEDULED\",\"appointmentId\":\"" + otherAppointment + "\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        String appointment = createAppointment(reception, patient, professional, slot(4));
        MvcTestResult scheduled = reception.post(
                "/api/v1/waitlist/" + id + "/resolve",
                "{\"status\":\"SCHEDULED\",\"appointmentId\":\"" + appointment + "\"}");
        assertThat(scheduled).hasStatus(HttpStatus.OK);
        assertThat((String) json(scheduled, "$.appointmentId")).isEqualTo(appointment);

        assertThat((List<?>) json(reception.get("/api/v1/waitlist"), "$")).hasSize(1);
        assertThat((List<?>) json(reception.get("/api/v1/waitlist?status=SCHEDULED"), "$"))
                .hasSize(1);
        assertThat(reception.post("/api/v1/waitlist/" + id + "/resolve", "{\"status\":\"REMOVED\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void isIsolatedPerClinicAndNeedsSchedulePermission() {
        String id = json(reception.post("/api/v1/waitlist", entry(patient, null, false)), "$.id");

        UUID otherClinic = data.clinic();
        Browser stranger = browser().login(data.member(otherClinic, "OWNER").email(), TestData.PASSWORD);
        assertThat((List<?>) json(stranger.get("/api/v1/waitlist"), "$")).isEmpty();
        assertThat(stranger.post("/api/v1/waitlist/" + id + "/resolve", "{\"status\":\"REMOVED\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(stranger.post("/api/v1/waitlist", entry(patient, null, false)))
                .hasStatus(HttpStatus.NOT_FOUND);

        Browser finance = browser().login(data.member(clinic, "FINANCE").email(), TestData.PASSWORD);
        assertThat(finance.get("/api/v1/waitlist")).hasStatus(HttpStatus.FORBIDDEN);

        assertThat(reception.post("/api/v1/waitlist/" + id + "/resolve", "{\"status\":\"REMOVED\"}"))
                .hasStatus(HttpStatus.OK);
        assertThat((List<?>) json(reception.get("/api/v1/waitlist?status=REMOVED"), "$"))
                .hasSize(1);
    }
}
