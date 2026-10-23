package br.com.odontosaas.flow;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createAppointment;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static br.com.odontosaas.support.Fixtures.createProfessional;
import static br.com.odontosaas.support.Fixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import br.com.odontosaas.communication.job.CommunicationJobs;
import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Estoque, comunicação, portal do paciente, relatórios, dashboard e planos da plataforma. */
class OperationsFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    @Autowired
    CommunicationJobs jobs;

    UUID clinic;
    Browser owner;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
    }

    @Test
    void inventoryMovementsKeepBalanceAndPreventNegativeStock() {
        MvcTestResult product = owner.post(
                "/api/v1/inventory/products", "{\"name\":\"Luva M\",\"unit\":\"cx\",\"minStock\":5,\"active\":true}");
        assertThat(product).hasStatus(HttpStatus.CREATED);
        String id = json(product, "$.id");

        MvcTestResult in = owner.post(
                "/api/v1/inventory/products/" + id + "/movements",
                "{\"type\":\"IN\",\"quantity\":10,\"lotNumber\":\"L1\",\"expiryDate\":\""
                        + LocalDate.now().plusDays(20) + "\"}");
        assertThat(new BigDecimal(json(in, "$.currentStock").toString())).isEqualByComparingTo("10");

        MvcTestResult tooMuch =
                owner.post("/api/v1/inventory/products/" + id + "/movements", "{\"type\":\"OUT\",\"quantity\":11}");
        assertThat(tooMuch).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        MvcTestResult adjust = owner.post(
                "/api/v1/inventory/products/" + id + "/movements",
                "{\"type\":\"ADJUST\",\"quantity\":4,\"reason\":\"Inventário\"}");
        assertThat((Boolean) json(adjust, "$.lowStock")).isTrue();

        MvcTestResult alerts = owner.get("/api/v1/inventory/alerts");
        assertThat((List<String>) json(alerts, "$.lowStock[*].id")).contains(id);
        assertThat((List<String>) json(alerts, "$.expiring[*].lotNumber")).contains("L1");
    }

    @Test
    void appointmentEmailsAndRemindersAreQueuedAndSent() {
        String professional = createProfessional(owner, "Dr. Lembrete");
        String patient = createPatient(owner, "Paciente Lembrete");
        String appointment = createAppointment(owner, patient, professional, slot(1).minusSeconds(3600 * 24 - 600));

        // Agendamento gera e-mail de confirmação; o lembrete é gerado pela rotina (consulta em menos de 24h).
        assertThat(jobs.generateReminders()).isGreaterThanOrEqualTo(1);
        MvcTestResult queued = owner.get("/api/v1/communication/messages?status=PENDING");
        List<String> types = json(queued, "$[*].type");
        assertThat(types).contains("APPOINTMENT_CREATED", "APPOINTMENT_REMINDER");

        assertThat(jobs.dispatch()).isGreaterThanOrEqualTo(2);
        verify(mailSender, atLeastOnce()).send(any(SimpleMailMessage.class));
        assertThat((List<?>) json(owner.get("/api/v1/communication/messages?status=PENDING"), "$"))
                .isEmpty();
        assertThat(superuserJdbc()
                        .queryForObject(
                                "select reminder_sent_at is not null from appointments where id = ?::uuid",
                                Boolean.class,
                                appointment))
                .isTrue();
    }

    @Test
    void patientWithoutEmailConsentReceivesNothing() {
        String professional = createProfessional(owner, "Dr. Sem Email");
        String patient = json(
                owner.post(
                        "/api/v1/patients",
                        ("{\"fullName\":\"Sem Consentimento\",\"birthDate\":\"1990-05-10\",\"cpf\":\"%s\","
                                        + "\"mobile\":\"11999990000\",\"email\":\"x@teste.example\",\"allowEmail\":false}")
                                .formatted(TestData.cpf())),
                "$.id");
        createAppointment(owner, patient, professional, slot(2));
        List<String> recipients = json(owner.get("/api/v1/communication/messages"), "$[*].toAddress");
        assertThat(recipients).doesNotContain("x@teste.example");
    }

    @Test
    void patientPortalShowsOnlyOwnDataAndAllowsActions() {
        String professional = createProfessional(owner, "Dra. Portal");
        String patient = createPatient(owner, "Paciente Portal");
        String other = createPatient(owner, "Outro Paciente");
        String mine = createAppointment(owner, patient, professional, slot(3));
        String notMine = createAppointment(owner, other, professional, slot(4));

        Browser portal = portalPatient(owner, patient);

        MvcTestResult overview = portal.get("/api/v1/public/portal/overview");
        assertThat(overview).hasStatusOk();
        assertThat((List<String>) json(overview, "$.upcoming[*].id")).containsExactly(mine);

        assertThat(portal.post(
                        "/api/v1/public/portal/appointments/respond",
                        "{\"appointmentId\":\"%s\",\"action\":\"CANCEL\"}".formatted(notMine)))
                .hasStatus(HttpStatus.NOT_FOUND);
        MvcTestResult confirmed = portal.post(
                "/api/v1/public/portal/appointments/respond",
                "{\"appointmentId\":\"%s\",\"action\":\"CONFIRM\"}".formatted(mine));
        assertThat((String) json(confirmed, "$.upcoming[0].status")).isEqualTo("CONFIRMED");

        MvcTestResult contact = portal.post(
                "/api/v1/public/portal/contact", "{\"mobile\":\"11911112222\",\"phone\":\"\",\"allowEmail\":true}");
        assertThat((String) json(contact, "$.patient.mobile")).isEqualTo("11911112222");

        assertThat(portal.post("/api/v1/public/portal/contact", "{\"mobile\":\"\"}"))
                .hasStatusOk();
        assertThat((String) json(portal.get("/api/v1/public/portal/overview"), "$.patient.mobile"))
                .isEqualTo("11911112222");

        assertThat(portal.post("/api/v1/public/portal/logout", null)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(portal.get("/api/v1/public/portal/overview")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reportsDashboardAndCsvExport() {
        String professional = createProfessional(owner, "=HYPERLINK(1)");
        String patient = createPatient(owner, "Paciente Relatório");
        createAppointment(owner, patient, professional, slot(5));
        LocalDate today = LocalDate.now();
        String period = "?from=%s&to=%s".formatted(today.minusDays(30), today.plusDays(30));

        assertThat(owner.get("/api/v1/reports/agenda" + period)).hasStatusOk();
        assertThat(owner.get("/api/v1/reports/production" + period)).hasStatusOk();
        assertThat(owner.get("/api/v1/reports/financial" + period)).hasStatusOk();
        assertThat(owner.get("/api/v1/reports/patients")).hasStatusOk();

        MvcTestResult csv = owner.get("/api/v1/reports/agenda/export" + period);
        assertThat(csv).hasStatusOk();
        String body = Browser.body(csv);
        assertThat(body).contains("'=HYPERLINK"); // fórmula neutralizada (CSV injection)

        MvcTestResult dashboard = owner.get("/api/v1/dashboard");
        assertThat(dashboard).hasStatusOk();
        assertThat((Object) json(dashboard, "$.activePatients")).isNotNull();

        Browser dentist = browser().login(data.member(clinic, "DENTIST").email(), TestData.PASSWORD);
        MvcTestResult limited = dentist.get("/api/v1/dashboard");
        assertThat((Object) json(limited, "$.receivedThisMonth")).isNull(); // sem finance:read
        assertThat(dentist.get("/api/v1/reports/agenda" + period)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void planFeaturesAndLimitsAreEnforcedOnTheServer() {
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        assertThat(admin.put(
                        "/api/v1/platform/clinics/" + clinic + "/subscription",
                        "{\"planCode\":\"ESSENCIAL\",\"status\":\"ACTIVE\"}"))
                .hasStatusOk();

        MvcTestResult blocked = owner.get("/api/v1/inventory/products");
        assertThat(blocked).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(blocked, "$.code")).isEqualTo("FEATURE_NOT_IN_PLAN");
        assertThat(owner.get("/api/v1/patients")).hasStatusOk(); // recurso essencial continua

        createProfessional(owner, "Prof 1");
        createProfessional(owner, "Prof 2");
        MvcTestResult limit = owner.post(
                "/api/v1/clinic/professionals",
                "{\"name\":\"%s\",\"cro\":\"999\",\"croState\":\"SP\",\"email\":\"p%s@teste.example\",\"color\":\"#000000\",\"active\":true}"
                        .formatted("Prof 3", TestData.unique()));
        assertThat(limit).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(limit, "$.code")).isEqualTo("PLAN_LIMIT_REACHED");

        assertThat(admin.put(
                        "/api/v1/platform/clinics/" + clinic + "/subscription",
                        "{\"planCode\":\"ESSENCIAL\",\"status\":\"CANCELLED\"}"))
                .hasStatusOk();
        assertThat((String) json(owner.get("/api/v1/patients"), "$.code")).isEqualTo("SUBSCRIPTION_INACTIVE");
    }

    @Test
    void platformSeesOnlyPlatformAuditAndMetrics() {
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        createPatient(owner, "Evento de clínica"); // gera evento COM clínica

        assertThat(admin.get("/api/v1/platform/metrics")).hasStatusOk();
        MvcTestResult audit = admin.get("/api/v1/platform/audit");
        assertThat(audit).hasStatusOk();
        List<String> resourceTypes = json(audit, "$[*].resourceType");
        assertThat(resourceTypes).doesNotContain("patient");
        assertThat((List<String>) json(audit, "$[*].action")).contains("LOGIN_SUCCEEDED");

        // A clínica vê a própria auditoria; quem não tem audit:read, não.
        assertThat((List<String>) json(owner.get("/api/v1/clinic/audit"), "$[*].resourceType"))
                .contains("patient");
        Browser reception = browser().login(data.member(clinic, "RECEPTION").email(), TestData.PASSWORD);
        assertThat(reception.get("/api/v1/clinic/audit")).hasStatus(HttpStatus.FORBIDDEN);

        assertThat(owner.get("/api/v1/platform/metrics")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void suspendedClinicLosesAccess() {
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        assertThat(admin.post("/api/v1/platform/clinics/" + clinic + "/status", "{\"status\":\"SUSPENDED\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(owner.get("/api/v1/patients")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((Object) json(owner.get("/api/v1/auth/me"), "$.activeClinic"))
                .isNull();
    }
}
