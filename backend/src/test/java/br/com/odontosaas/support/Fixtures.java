package br.com.odontosaas.support;

import static br.com.odontosaas.support.Browser.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Atalhos para criar registros de negócio pela API (como o front-end faria). Dados fictícios. */
public final class Fixtures {

    private Fixtures() {}

    public static String createProfessional(Browser owner, String name) {
        MvcTestResult r = owner.post(
                "/api/v1/clinic/professionals",
                "{\"name\":\"%s\",\"cro\":\"12345\",\"croState\":\"SP\",\"color\":\"#9C7A5B\",\"active\":true}"
                        .formatted(name));
        assertThat(r).hasStatus(HttpStatus.CREATED);
        return json(r, "$.id");
    }

    public static String createProcedure(Browser owner, String name, String price) {
        MvcTestResult r = owner.post(
                "/api/v1/clinic/procedures",
                "{\"name\":\"%s\",\"category\":\"Clínico\",\"defaultPrice\":%s,\"durationMinutes\":30,\"active\":true}"
                        .formatted(name, price));
        assertThat(r).hasStatus(HttpStatus.CREATED);
        return json(r, "$.id");
    }

    public static String createPatient(Browser browser, String name) {
        MvcTestResult r = browser.post(
                "/api/v1/patients",
                ("{\"fullName\":\"%s\",\"birthDate\":\"1990-05-10\",\"mobile\":\"11999990000\","
                                + "\"email\":\"paciente%s@teste.example\",\"allowEmail\":true}")
                        .formatted(name, TestData.unique()));
        assertThat(r).hasStatus(HttpStatus.CREATED);
        return json(r, "$.id");
    }

    /** Horário futuro "redondo" (amanhã + N horas), para evitar colisões entre testes. */
    public static Instant slot(int hoursFromTomorrow) {
        return Instant.now()
                .truncatedTo(ChronoUnit.HOURS)
                .plus(1, ChronoUnit.DAYS)
                .plus(hoursFromTomorrow, ChronoUnit.HOURS);
    }

    public static String appointmentJson(String patientId, String professionalId, Instant start, int minutes) {
        return "{\"patientId\":\"%s\",\"professionalId\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                .formatted(patientId, professionalId, start, start.plus(minutes, ChronoUnit.MINUTES));
    }

    public static String createAppointment(Browser browser, String patientId, String professionalId, Instant start) {
        MvcTestResult r = browser.post("/api/v1/appointments", appointmentJson(patientId, professionalId, start, 30));
        assertThat(r).hasStatus(HttpStatus.CREATED);
        return json(r, "$.id");
    }
}
