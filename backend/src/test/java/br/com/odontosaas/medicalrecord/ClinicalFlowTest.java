package br.com.odontosaas.medicalrecord;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static br.com.odontosaas.support.Fixtures.createProfessional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ClinicalFlowTest extends PostgresIntegrationTest {

    static final String SIGNATURE =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk";

    @Autowired
    TestData data;

    @Autowired
    JdbcTemplate jdbc;

    UUID clinic;
    Browser owner;
    String professional;
    String patient;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        professional = createProfessional(owner, "Dr. Prontuário");
        patient = createPatient(owner, "Paciente Clínico");
    }

    @Test
    void anamnesisHighlightsAlertsAndKeepsHistory() {
        String body = "{\"answers\":{\"allergies\":{\"answer\":\"YES\",\"detail\":\"Penicilina\"},"
                + "\"diabetes\":{\"answer\":\"NO\"},\"smoking\":{\"answer\":\"YES\"}},\"notes\":\"Primeira consulta\"}";
        assertThat(owner.post("/api/v1/patients/" + patient + "/anamneses", body))
                .hasStatus(HttpStatus.CREATED);
        assertThat(owner.post("/api/v1/patients/" + patient + "/anamneses", body.replace("Penicilina", "Dipirona")))
                .hasStatus(HttpStatus.CREATED);

        MvcTestResult summary = owner.get("/api/v1/patients/" + patient + "/record");
        assertThat(summary).hasStatusOk();
        List<String> alertIds = json(summary, "$.alerts[*].questionId");
        assertThat(alertIds).containsExactly("allergies"); // fumar não gera alerta no modelo padrão
        assertThat((String) json(summary, "$.alerts[0].detail")).isEqualTo("Dipirona");
        assertThat((List<?>) json(owner.get("/api/v1/patients/" + patient + "/anamneses"), "$"))
                .hasSize(2);

        MvcTestResult unknown = owner.post(
                "/api/v1/patients/" + patient + "/anamneses", "{\"answers\":{\"inventada\":{\"answer\":\"YES\"}}}");
        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void openingTheRecordIsAudited() {
        owner.get("/api/v1/patients/" + patient + "/record");
        Integer views = superuserJdbc()
                .queryForObject(
                        "select count(*) from audit_events where action = 'CLINICAL_RECORD_VIEWED' and resource_id = ?",
                        Integer.class,
                        patient);
        assertThat(views).isEqualTo(1);
    }

    @Test
    void dentalChartValidatesTeethAndOnlyVoidsWithReason() {
        assertThat(owner.post(
                        "/api/v1/patients/" + patient + "/dental-chart",
                        "{\"tooth\":19,\"condition\":\"CARIES\",\"status\":\"EXISTING\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        MvcTestResult created = owner.post(
                "/api/v1/patients/" + patient + "/dental-chart",
                "{\"tooth\":36,\"surfaces\":\"MO\",\"condition\":\"CARIES\",\"status\":\"EXISTING\"}");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String entry = json(created, "$.id");
        assertThat(owner.post(
                        "/api/v1/patients/" + patient + "/dental-chart",
                        "{\"tooth\":55,\"condition\":\"SEALANT\",\"status\":\"DONE\"}"))
                .hasStatus(HttpStatus.CREATED); // dente decíduo

        assertThat(owner.post("/api/v1/patients/" + patient + "/dental-chart/" + entry + "/void", "{\"reason\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(owner.post(
                        "/api/v1/patients/" + patient + "/dental-chart/" + entry + "/void",
                        "{\"reason\":\"Registrado no dente errado\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);
        List<String> voided =
                json(owner.get("/api/v1/patients/" + patient + "/dental-chart"), "$[?(@.voidedAt != null)].id");
        assertThat(voided).containsExactly(entry);
    }

    @Test
    void clinicalNotesCannotBeEditedOrDeletedEvenWithSql() {
        MvcTestResult note = owner.post(
                "/api/v1/patients/" + patient + "/notes",
                "{\"content\":\"Profilaxia realizada.\",\"professionalId\":\"" + professional + "\"}");
        assertThat(note).hasStatus(HttpStatus.CREATED);
        String noteId = json(note, "$.id");

        assertThatThrownBy(() -> TenantContext.runAs(
                        clinic,
                        null,
                        () -> jdbc.update("update clinical_notes set content = 'x' where id = ?::uuid", noteId)))
                .rootCause()
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.runAs(
                        clinic, null, () -> jdbc.update("delete from clinical_notes where id = ?::uuid", noteId)))
                .rootCause()
                .hasMessageContaining("permission denied");

        // Retificação = nova evolução apontando para a anterior.
        assertThat(owner.post(
                        "/api/v1/patients/" + patient + "/notes",
                        "{\"content\":\"Retificação: profilaxia + flúor.\",\"amendsNoteId\":\"" + noteId + "\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void receptionCannotReadClinicalRecord() {
        Browser reception = browser().login(data.member(clinic, "RECEPTION").email(), TestData.PASSWORD);
        assertThat(reception.get("/api/v1/patients/" + patient + "/record")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(reception.get("/api/v1/patients/" + patient + "/notes")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherClinicCannotReadOrWriteThisRecord() {
        Browser intruder = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(intruder.get("/api/v1/patients/" + patient + "/record")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(intruder.get("/api/v1/patients/" + patient + "/notes")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(intruder.post("/api/v1/patients/" + patient + "/notes", "{\"content\":\"invasão\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void consentIsRenderedSignedInClinicAndTextIsFrozen() {
        String template = createConsentTemplate();
        MvcTestResult issued = owner.post(
                "/api/v1/patients/" + patient + "/consents",
                "{\"templateId\":\"%s\",\"professionalId\":\"%s\",\"procedureDescription\":\"Exodontia do 38\"}"
                        .formatted(template, professional));
        assertThat(issued).hasStatus(HttpStatus.CREATED);
        String consent = json(issued, "$.id");
        String body = json(issued, "$.body");
        assertThat(body)
                .contains("Paciente Clínico")
                .contains("Exodontia do 38")
                .doesNotContain("{{");

        // Editar o modelo NÃO altera o termo já emitido.
        assertThat(owner.put(
                        "/api/v1/clinic/consent-templates/" + template,
                        "{\"title\":\"Termo v2\",\"body\":\"Outro texto {{paciente}}\"}"))
                .hasStatusOk();

        MvcTestResult signed = owner.post(
                "/api/v1/patients/" + patient + "/consents/" + consent + "/sign",
                "{\"signerName\":\"Paciente Clínico\",\"signatureImage\":\"%s\",\"accepted\":true}"
                        .formatted(SIGNATURE));
        assertThat(signed).hasStatusOk();
        assertThat((String) json(signed, "$.status")).isEqualTo("SIGNED");
        assertThat((String) json(signed, "$.body")).isEqualTo(body);

        MvcTestResult again = owner.post(
                "/api/v1/patients/" + patient + "/consents/" + consent + "/sign",
                "{\"signerName\":\"X\",\"signatureImage\":\"%s\",\"accepted\":true}".formatted(SIGNATURE));
        assertThat(again).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void patientSignsThroughPublicLink() {
        String template = createConsentTemplate();
        String consent = json(
                owner.post(
                        "/api/v1/patients/" + patient + "/consents",
                        "{\"templateId\":\"" + template + "\",\"procedureDescription\":\"Clareamento\"}"),
                "$.id");
        MvcTestResult link =
                owner.post("/api/v1/patients/" + patient + "/consents/" + consent + "/link", "{\"sendEmail\":false}");
        assertThat(link).hasStatusOk();
        String url = json(link, "$.url");
        String token = url.substring(url.indexOf("#token=") + 7);

        MvcTestResult lookup = browser().post("/api/v1/public/consents/lookup", "{\"token\":\"" + token + "\"}");
        assertThat(lookup).hasStatusOk();
        assertThat((String) json(lookup, "$.body")).contains("Clareamento");

        MvcTestResult notAccepted = browser()
                .post(
                        "/api/v1/public/consents/sign",
                        "{\"token\":\"%s\",\"signerName\":\"P\",\"signatureImage\":\"%s\",\"accepted\":false}"
                                .formatted(token, SIGNATURE));
        assertThat(notAccepted).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult signed = browser()
                .post(
                        "/api/v1/public/consents/sign",
                        "{\"token\":\"%s\",\"signerName\":\"Paciente Clínico\",\"signerDocument\":\"123\",\"signatureImage\":\"%s\",\"accepted\":true}"
                                .formatted(token, SIGNATURE));
        assertThat(signed).hasStatusOk();
        assertThat((String) json(signed, "$.status")).isEqualTo("SIGNED");
        assertThat((String) json(owner.get("/api/v1/patients/" + patient + "/consents/" + consent), "$.signedIp"))
                .isNotBlank();
    }

    @Test
    void attachmentsCheckRealFileTypeAndAreAuditedOnDownload() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
        MvcTestResult uploaded = upload(owner, "radiografia.png", png, "XRAY");
        assertThat(uploaded).hasStatus(HttpStatus.CREATED);
        String attachment = json(uploaded, "$.id");

        // Executável renomeado para .png é recusado (o conteúdo não é imagem).
        MvcTestResult fake = upload(owner, "virus.png", "MZ\u0090\u0000 executável".getBytes(), "PHOTO");
        assertThat(fake).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult download = owner.get("/api/v1/patients/" + patient + "/attachments/" + attachment + "/download");
        assertThat(download).hasStatusOk().hasHeader("X-Content-Type-Options", "nosniff");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(png);
        Integer audited = superuserJdbc()
                .queryForObject(
                        "select count(*) from audit_events where action = 'ATTACHMENT_DOWNLOADED' and resource_id = ?",
                        Integer.class,
                        attachment);
        assertThat(audited).isEqualTo(1);

        Browser intruder = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(intruder.get("/api/v1/patients/" + patient + "/attachments/" + attachment + "/download"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void documentsAreIssuedWithProfessionalData() {
        MvcTestResult doc = owner.post(
                "/api/v1/patients/" + patient + "/documents",
                "{\"professionalId\":\"%s\",\"type\":\"PRESCRIPTION\",\"title\":\"Receituário\",\"content\":\"Uso oral...\"}"
                        .formatted(professional));
        assertThat(doc).hasStatus(HttpStatus.CREATED);
        assertThat((String) json(doc, "$.professionalCro")).isEqualTo("CRO-SP 12345");
    }

    private String createConsentTemplate() {
        MvcTestResult t = owner.post(
                "/api/v1/clinic/consent-templates",
                "{\"title\":\"Termo cirúrgico\",\"body\":\"Eu, {{paciente}}, autorizo {{procedimento}} em {{data}}.\"}");
        assertThat(t).hasStatus(HttpStatus.CREATED);
        return json(t, "$.id");
    }

    private MvcTestResult upload(Browser who, String name, byte[] content, String category) {
        MockMultipartFile file = new MockMultipartFile("file", name, "image/png", content);
        String csrf = "csrf-upload";
        return mvc.post()
                .uri("/api/v1/patients/" + patient + "/attachments")
                .multipart()
                .file(file)
                .param("category", category)
                .cookie(new Cookie("XSRF-TOKEN", csrf), new Cookie(Browser.SESSION_COOKIE, who.session()))
                .header("X-XSRF-TOKEN", csrf)
                .exchange();
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> none() {
        return Map.of();
    }
}
