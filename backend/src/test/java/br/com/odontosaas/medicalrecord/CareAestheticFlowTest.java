package br.com.odontosaas.medicalrecord;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static br.com.odontosaas.support.Fixtures.createProfessional;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Fichas de anamnese, categorias de termos, orientações ao paciente e registro de harmonização orofacial. */
class CareAestheticFlowTest extends PostgresIntegrationTest {

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
        professional = createProfessional(owner, "Dra. Harmonia");
        patient = createPatient(owner, "Paciente Estética");
    }

    private void enableAesthetics(boolean enabled) {
        assertThat(owner.put(
                        "/api/v1/clinic/settings",
                        ("{\"name\":\"Clínica Teste\",\"defaultAppointmentMinutes\":30,\"remindersEnabled\":true,"
                                        + "\"reminderHoursBefore\":24,\"aestheticsEnabled\":%s}")
                                .formatted(enabled)))
                .hasStatusOk();
    }

    @Test
    void anamnesisFormsAreSeparateAndAlertsComeFromEachForm() {
        List<String> kinds = json(owner.get("/api/v1/clinic/anamnesis-forms"), "$[*].kind");
        assertThat(kinds).containsExactly("DENTAL");

        enableAesthetics(true);
        MvcTestResult forms = owner.get("/api/v1/clinic/anamnesis-forms");
        assertThat((List<String>) json(forms, "$[*].kind")).containsExactly("DENTAL", "AESTHETIC");
        String aestheticForm = json(forms, "$[1].id");

        owner.post(
                "/api/v1/patients/" + patient + "/anamneses",
                "{\"answers\":{\"allergies\":{\"answer\":\"YES\",\"detail\":\"Látex\"}}}");
        MvcTestResult filled = owner.post(
                "/api/v1/patients/" + patient + "/anamneses",
                "{\"formId\":\"%s\",\"answers\":{\"pregnancy\":{\"answer\":\"YES\"},\"herpes\":{\"answer\":\"NO\"}}}"
                        .formatted(aestheticForm));
        assertThat(filled).hasStatus(HttpStatus.CREATED);
        assertThat((String) json(filled, "$.formKind")).isEqualTo("AESTHETIC");

        List<String> alerts = json(owner.get("/api/v1/patients/" + patient + "/record"), "$.alerts[*].questionId");
        assertThat(alerts).containsExactlyInAnyOrder("allergies", "pregnancy");

        List<String> recent = json(owner.get("/api/v1/anamneses?kind=AESTHETIC&alertsOnly=true"), "$[*].patientId");
        assertThat(recent).containsExactly(patient);

        // Pergunta de outra ficha é rejeitada.
        assertThat(owner.post(
                        "/api/v1/patients/" + patient + "/anamneses",
                        "{\"formId\":\"%s\",\"answers\":{\"bruxism\":{\"answer\":\"YES\"}}}".formatted(aestheticForm)))
                .hasStatus(HttpStatus.BAD_REQUEST);

        // Ficha desativada não aceita novos preenchimentos.
        owner.post("/api/v1/clinic/anamnesis-forms/" + aestheticForm + "/active", "{\"active\":false}");
        assertThat(owner.post(
                        "/api/v1/patients/" + patient + "/anamneses",
                        "{\"formId\":\"%s\",\"answers\":{}}".formatted(aestheticForm)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void consentExamplesHaveCategoriesAndClinicWideListFilters() {
        MvcTestResult added = owner.post("/api/v1/clinic/consent-templates/examples", "{}");
        assertThat(added).hasStatusOk();
        assertThat((Integer) json(added, "$.created")).isEqualTo(4);
        assertThat((Integer) json(owner.post("/api/v1/clinic/consent-templates/examples", "{}"), "$.created"))
                .isZero();

        MvcTestResult templates = owner.get("/api/v1/clinic/consent-templates");
        List<String> categories = json(templates, "$[*].category");
        assertThat(categories).contains("DENTAL", "AESTHETIC", "IMAGE", "PRIVACY");
        String aesthetic =
                json(templates, "$[?(@.category == 'AESTHETIC')].id").toString().replaceAll("[\\[\\]\"]", "");

        MvcTestResult issued = owner.post(
                "/api/v1/patients/" + patient + "/consents",
                "{\"templateId\":\"%s\",\"professionalId\":\"%s\",\"procedureDescription\":\"Toxina botulínica\"}"
                        .formatted(aesthetic, professional));
        assertThat(issued).hasStatus(HttpStatus.CREATED);
        assertThat((String) json(issued, "$.category")).isEqualTo("AESTHETIC");
        assertThat((String) json(issued, "$.body"))
                .contains("Toxina botulínica")
                .contains("Dra. Harmonia");

        assertThat((List<String>)
                        json(owner.get("/api/v1/consents?category=AESTHETIC&status=PENDING"), "$[*].patientId"))
                .containsExactly(patient);
        assertThat((List<?>) json(owner.get("/api/v1/consents?category=IMAGE"), "$"))
                .isEmpty();
    }

    @Test
    void careInstructionsBecomeRecordDocumentAndAreEmailed() {
        MvcTestResult template = owner.post(
                "/api/v1/clinic/care-templates",
                "{\"title\":\"Cuidados após extração\",\"category\":\"DENTAL\",\"moment\":\"AFTER\","
                        + "\"body\":\"Texto definido pela clínica.\",\"active\":true}");
        assertThat(template).hasStatus(HttpStatus.CREATED);

        MvcTestResult doc = owner.post(
                "/api/v1/patients/" + patient + "/care-instructions",
                "{\"professionalId\":\"%s\",\"title\":\"Cuidados após extração\",\"content\":\"Texto definido pela clínica.\",\"sendEmail\":true}"
                        .formatted(professional));
        assertThat(doc).hasStatus(HttpStatus.CREATED);
        assertThat((String) json(doc, "$.type")).isEqualTo("INSTRUCTIONS");

        assertThat((List<String>) json(owner.get("/api/v1/clinical-documents?type=INSTRUCTIONS"), "$[*].patientId"))
                .containsExactly(patient);
        Integer queued = superuserJdbc()
                .queryForObject(
                        "select count(*) from outbound_messages where patient_id = ? and type = 'CARE_INSTRUCTIONS'"
                                + " and body like '%Texto definido pela clínica.%'",
                        Integer.class, UUID.fromString(patient));
        assertThat(queued).isEqualTo(1);
    }

    private String aestheticJson(String batch, LocalDate expiry) {
        return ("{\"professionalId\":\"%s\",\"procedureType\":\"TOXIN\",\"productName\":\"Produto Fictício\","
                        + "\"batch\":\"%s\",\"expiryDate\":\"%s\",\"unit\":\"U\",\"returnDate\":\"%s\","
                        + "\"regions\":[{\"region\":\"Glabela\",\"quantity\":10},{\"region\":\"Fronte\",\"quantity\":8.5}]}")
                .formatted(professional, batch, expiry, LocalDate.now().plusDays(15));
    }

    @Test
    void aestheticRecordsTrackBatchAndRequireTheOption() {
        String batch = "LOTE-" + TestData.unique();
        String url = "/api/v1/patients/" + patient + "/aesthetic-procedures";
        assertThat(owner.post(url, aestheticJson(batch, LocalDate.now().plusYears(1))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        enableAesthetics(true);
        MvcTestResult created =
                owner.post(url, aestheticJson(batch, LocalDate.now().plusYears(1)));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat((Double) json(created, "$.totalQuantity")).isEqualTo(18.5);
        String id = json(created, "$.id");

        assertThat(owner.post(url, aestheticJson(batch, LocalDate.now().minusDays(1))))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat((List<String>)
                        json(owner.get("/api/v1/aesthetic-procedures?batch=" + batch.toLowerCase()), "$[*].id"))
                .containsExactly(id);
        assertThat((List<String>) json(
                        owner.get("/api/v1/aesthetic-procedures?returnsUntil="
                                + LocalDate.now().plusDays(30)),
                        "$[*].id"))
                .contains(id);

        Browser intruder = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat((List<?>) json(intruder.get("/api/v1/aesthetic-procedures?batch=" + batch), "$"))
                .isEmpty();
        assertThat(intruder.get(url)).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(owner.post(url + "/" + id + "/void", "{\"reason\":\"Registrado no paciente errado\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat((String) json(owner.get(url), "$[0].voidReason")).isEqualTo("Registrado no paciente errado");
    }
}
