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

class ScheduleBlockTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;
    String professional;
    String other;
    String patient;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        professional = createProfessional(owner, "Dra. Bloqueio");
        other = createProfessional(owner, "Dr. Livre");
        patient = createPatient(owner, "Paciente Bloqueio");
    }

    private static String block(String professionalId, Instant start, Instant end, String reason) {
        return "{\"professionalId\":%s,\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"reason\":\"%s\"}"
                .formatted(professionalId == null ? "null" : "\"" + professionalId + "\"", start, end, reason);
    }

    @Test
    void professionalBlockPreventsBookingOnlyForThatProfessional() {
        Instant start = slot(9);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        String existing = createAppointment(owner, patient, professional, start.plus(1, ChronoUnit.HOURS));

        MvcTestResult created = owner.post("/api/v1/schedule-blocks", block(professional, start, end, "Curso"));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat((Integer) json(created, "$.appointmentsInside")).isEqualTo(1);
        assertThat((String) json(created, "$.professionalName")).isEqualTo("Dra. Bloqueio");

        MvcTestResult refused = owner.post(
                "/api/v1/appointments", appointmentJson(patient, professional, start.plus(2, ChronoUnit.HOURS), 30));
        assertThat(refused).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(refused, "$.code")).isEqualTo("SCHEDULE_BLOCKED");
        assertThat((String) json(refused, "$.detail")).contains("Curso");

        // Outro profissional continua livre; o atendimento que já existia não é apagado.
        assertThat(owner.post(
                        "/api/v1/appointments", appointmentJson(patient, other, start.plus(2, ChronoUnit.HOURS), 30)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(owner.get("/api/v1/appointments/" + existing)).hasStatusOk();

        MvcTestResult list = owner.get("/api/v1/schedule-blocks?from=%s&to=%s".formatted(start, end));
        String id = json(list, "$[0].id");
        assertThat(owner.delete("/api/v1/schedule-blocks/" + id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(owner.post(
                        "/api/v1/appointments",
                        appointmentJson(patient, professional, start.plus(2, ChronoUnit.HOURS), 30)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void clinicWideBlockStopsEveryoneAndHidesFreeSlots() {
        Instant start = slot(0);
        Instant end = start.plus(24, ChronoUnit.HOURS);
        assertThat(owner.post("/api/v1/schedule-blocks", block(null, start, end, "Feriado")))
                .hasStatus(HttpStatus.CREATED);

        for (String prof : List.of(professional, other)) {
            MvcTestResult refused = owner.post(
                    "/api/v1/appointments", appointmentJson(patient, prof, start.plus(10, ChronoUnit.HOURS), 30));
            assertThat((String) json(refused, "$.code")).isEqualTo("SCHEDULE_BLOCKED");
        }
    }

    @Test
    void blocksAreIsolatedPerClinicAndNeedWritePermission() {
        Instant start = slot(9);
        Browser reception = browser().login(data.member(clinic, "FINANCE").email(), TestData.PASSWORD);
        assertThat(reception.post("/api/v1/schedule-blocks", block(null, start, start.plusSeconds(3600), "X")))
                .hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult created = owner.post("/api/v1/schedule-blocks", block(null, start, start.plusSeconds(3600), "Y"));
        String id = json(created, "$.id");
        Browser otherOwner = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(otherOwner.delete("/api/v1/schedule-blocks/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((List<?>) json(
                        otherOwner.get(
                                "/api/v1/schedule-blocks?from=%s&to=%s".formatted(start, start.plusSeconds(3600))),
                        "$"))
                .isEmpty();
    }
}
