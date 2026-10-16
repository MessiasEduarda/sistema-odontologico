package br.com.odontosaas.scheduling;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.appointmentJson;
import static br.com.odontosaas.support.Fixtures.createAppointment;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static br.com.odontosaas.support.Fixtures.createProfessional;
import static br.com.odontosaas.support.Fixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SchedulingFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;
    String professional;
    String patient;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        professional = createProfessional(owner, "Dra. Teste");
        patient = createPatient(owner, "Paciente Agenda");
    }

    @Test
    void createsAndListsAppointmentsInPeriod() {
        Instant start = slot(9);
        String id = createAppointment(owner, patient, professional, start);

        MvcTestResult list = owner.get("/api/v1/appointments?from=%s&to=%s"
                .formatted(start.minus(1, ChronoUnit.HOURS), start.plus(1, ChronoUnit.HOURS)));
        assertThat(list).hasStatusOk();
        List<String> ids = json(list, "$[*].id");
        assertThat(ids).containsExactly(id);
        assertThat((String) json(list, "$[0].patientName")).isEqualTo("Paciente Agenda");
        assertThat((String) json(list, "$[0].status")).isEqualTo("SCHEDULED");
    }

    @Test
    void databaseRejectsOverlappingAppointmentsForSameProfessional() {
        Instant start = slot(10);
        createAppointment(owner, patient, professional, start);
        String other = createPatient(owner, "Outro Paciente");

        MvcTestResult conflict = owner.post(
                "/api/v1/appointments", appointmentJson(other, professional, start.plus(15, ChronoUnit.MINUTES), 30));

        assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(conflict, "$.code")).isEqualTo("SCHEDULE_CONFLICT");

        // Encostado (termina quando o outro começa) é permitido.
        assertThat(owner.post(
                        "/api/v1/appointments",
                        appointmentJson(other, professional, start.plus(30, ChronoUnit.MINUTES), 30)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void cancelledAppointmentFreesTheSlot() {
        Instant start = slot(11);
        String id = createAppointment(owner, patient, professional, start);
        assertThat(owner.post(
                        "/api/v1/appointments/" + id + "/status", "{\"status\":\"CANCELLED\",\"reason\":\"Pedido\"}"))
                .hasStatusOk();

        assertThat(owner.post("/api/v1/appointments", appointmentJson(patient, professional, start, 30)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void statusMachineRejectsInvalidTransitionsAndKeepsHistory() {
        String id = createAppointment(owner, patient, professional, slot(12));

        MvcTestResult invalid = owner.post("/api/v1/appointments/" + id + "/status", "{\"status\":\"COMPLETED\"}");
        assertThat(invalid).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(invalid, "$.code")).isEqualTo("INVALID_STATUS_TRANSITION");

        assertThat(owner.post("/api/v1/appointments/" + id + "/status", "{\"status\":\"CONFIRMED\"}"))
                .hasStatusOk();
        assertThat(owner.post("/api/v1/appointments/" + id + "/status", "{\"status\":\"ARRIVED\"}"))
                .hasStatusOk();
        assertThat(owner.post("/api/v1/appointments/" + id + "/status", "{\"status\":\"COMPLETED\"}"))
                .hasStatusOk();

        MvcTestResult detail = owner.get("/api/v1/appointments/" + id);
        List<String> actions = json(detail, "$.history[*].action");
        assertThat(actions).containsExactly("CREATED", "STATUS_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED");

        MvcTestResult closed =
                owner.put("/api/v1/appointments/" + id, appointmentJson(patient, professional, slot(14), 30));
        assertThat(closed).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void cancellationRequiresReason() {
        String id = createAppointment(owner, patient, professional, slot(13));
        MvcTestResult result = owner.post("/api/v1/appointments/" + id + "/status", "{\"status\":\"CANCELLED\"}");
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void availabilityUsesWorkingHoursAndBusySlots() {
        Instant tomorrow = slot(0);
        java.time.LocalDate date =
                tomorrow.atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate();
        int weekday = date.getDayOfWeek().getValue();
        assertThat(owner.put(
                        "/api/v1/clinic/professionals/" + professional + "/schedule",
                        "{\"slots\":[{\"weekday\":%d,\"startTime\":\"08:00\",\"endTime\":\"10:00\"}]}"
                                .formatted(weekday)))
                .hasStatusOk();
        Instant eight = date.atTime(8, 0)
                .atZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .toInstant();
        createAppointment(owner, patient, professional, eight);

        MvcTestResult free = owner.get("/api/v1/appointments/availability?professionalId=%s&date=%s&durationMinutes=30"
                .formatted(professional, date));

        assertThat(free).hasStatusOk();
        List<String> starts = json(free, "$[*].startsAt");
        assertThat(starts).hasSize(3).doesNotContain(eight.toString());
    }

    @Test
    void patientConfirmsThroughPublicLink() {
        String id = createAppointment(owner, patient, professional, slot(15));
        String token = superuserIssueToken(id);

        MvcTestResult lookup = browser().post("/api/v1/public/appointments/lookup", "{\"token\":\"" + token + "\"}");
        assertThat(lookup).hasStatusOk();
        assertThat((Boolean) json(lookup, "$.canRespond")).isTrue();
        assertThat(Browser.body(lookup)).doesNotContain("notes").doesNotContain("cpf");

        MvcTestResult confirmed = browser()
                .post(
                        "/api/v1/public/appointments/respond",
                        "{\"token\":\"%s\",\"action\":\"CONFIRM\"}".formatted(token));
        assertThat(confirmed).hasStatusOk();
        assertThat((String) json(confirmed, "$.status")).isEqualTo("CONFIRMED");
    }

    @Test
    void appointmentsOfAnotherClinicAreInvisible() {
        String id = createAppointment(owner, patient, professional, slot(16));
        UUID otherClinic = data.clinic();
        Browser intruder = browser().login(data.member(otherClinic, "OWNER").email(), TestData.PASSWORD);

        assertThat(intruder.get("/api/v1/appointments/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(intruder.get("/api/v1/patients/" + patient)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(intruder.post("/api/v1/appointments/" + id + "/status", "{\"status\":\"CONFIRMED\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        // Usar paciente/profissional da outra clínica num agendamento próprio também é bloqueado.
        assertThat(intruder.post("/api/v1/appointments", appointmentJson(patient, professional, slot(17), 30)))
                .hasStatus(HttpStatus.NOT_FOUND);
        List<String> visible = json(intruder.get("/api/v1/patients"), "$.items[*].id");
        assertThat(visible).doesNotContain(patient);
    }

    @Test
    void receptionSchedulesButCannotManageProfessionals() {
        Browser reception = browser().login(data.member(clinic, "RECEPTION").email(), TestData.PASSWORD);
        assertThat(reception.post("/api/v1/appointments", appointmentJson(patient, professional, slot(18), 30)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(reception.post(
                        "/api/v1/clinic/professionals", "{\"name\":\"X\",\"color\":\"#000000\",\"active\":true}"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private String superuserIssueToken(String appointmentId) {
        String raw = br.com.odontosaas.shared.SecureTokens.generate();
        superuserJdbc()
                .update(
                        "update appointments set confirmation_token_hash = ? where id = ?::uuid",
                        br.com.odontosaas.shared.SecureTokens.sha256Hex(raw),
                        appointmentId);
        return raw;
    }

    @Test
    void staffGeneratesPatientLinkForWhatsappAndPatientCanUseIt() {
        String id = createAppointment(owner, patient, professional, slot(11));
        MvcTestResult link = owner.post("/api/v1/appointments/" + id + "/patient-link", "{}");
        assertThat(link).hasStatusOk();
        String path = json(link, "$.path");
        assertThat(path).startsWith("/consulta#token=");
        String token = path.substring("/consulta#token=".length());

        MvcTestResult lookup = browser().post("/api/v1/public/appointments/lookup", "{\"token\":\"" + token + "\"}");
        assertThat(lookup).hasStatusOk();

        Browser other = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(other.post("/api/v1/appointments/" + id + "/patient-link", "{}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
