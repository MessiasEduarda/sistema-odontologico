package br.com.odontosaas.flow;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static br.com.odontosaas.support.Fixtures.createProfessional;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import br.com.odontosaas.support.TestData.Person;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Regressões dos achados de severidade alta do relatório de QA: bloqueio de plano por URL codificada, limite de
 * pacientes na importação e na reativação, papéis no reconvite, autoria de documentos clínicos, campos booleanos
 * omitidos e alertas de anamnese.
 */
class HighSeverityRegressionTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;
    Browser admin;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
    }

    private void setPlan(String plan) {
        assertThat(admin.put(
                        "/api/v1/platform/clinics/" + clinic + "/subscription",
                        "{\"planCode\":\"%s\",\"status\":\"ACTIVE\"}".formatted(plan)))
                .hasStatusOk();
    }

    // ------------------------------------------------------------------ 1. URL codificada

    @Test
    void planFeatureGateCannotBeBypassedWithEncodedPath() {
        setPlan("ESSENCIAL");

        assertThat(json(owner.get("/api/v1/inventory/products"), "$.code").toString())
                .isEqualTo("FEATURE_NOT_IN_PLAN");
        // %69 = "i": o Spring roteia para /inventory, então o bloqueio precisa enxergar o mesmo caminho.
        MvcTestResult encoded = owner.getEncoded("/api/v1/%69nventory/products");
        assertThat(encoded).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(encoded, "$.code")).isEqualTo("FEATURE_NOT_IN_PLAN");
        assertThat(owner.getEncoded("/api/v1/repo%72ts/patients")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelledSubscriptionBlocksTheWholeClinicArea() {
        assertThat(admin.put(
                        "/api/v1/platform/clinics/" + clinic + "/subscription",
                        "{\"planCode\":\"CLINICA_PLUS\",\"status\":\"CANCELLED\"}"))
                .hasStatusOk();

        for (String path : List.of(
                "/api/v1/clinic/professionals",
                "/api/v1/waitlist",
                "/api/v1/consents",
                "/api/v1/clinical-documents",
                "/api/v1/patients")) {
            MvcTestResult result = owner.get(path);
            assertThat(result).as(path).hasStatus(HttpStatus.FORBIDDEN);
            assertThat((String) json(result, "$.code")).as(path).isEqualTo("SUBSCRIPTION_INACTIVE");
        }
        assertThat(owner.get("/api/v1/clinic/subscription")).hasStatusOk(); // tela de pagamento continua
    }

    // ------------------------------------------------------------------ 2. Limite de pacientes

    private void limitPatientsTo(int max) {
        String code = "QA" + TestData.unique().replace("-", "_");
        assertThat(admin.post(
                        "/api/v1/platform/plans",
                        ("{\"code\":\"%s\",\"name\":\"Limite %d\",\"priceMonthly\":0,\"maxPatients\":%d,"
                                        + "\"features\":[\"AGENDA\",\"PATIENTS\",\"CLINICAL\"],\"active\":true}")
                                .formatted(code, max, max)))
                .hasStatus(HttpStatus.CREATED);
        setPlan(code);
    }

    private static String importJson(boolean dryRun, String... names) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) rows.append(',');
            rows.append("{\"line\":%d,\"fullName\":\"%s\",\"birthDate\":\"1990-01-01\"}".formatted(i + 1, names[i]));
        }
        return "{\"dryRun\":%s,\"rows\":[%s]}".formatted(dryRun, rows);
    }

    @Test
    void importAndReactivationRespectThePatientLimit() {
        String first = createPatient(owner, "Paciente Um");
        createPatient(owner, "Paciente Dois");
        limitPatientsTo(3); // 2 ativos, resta 1 vaga

        MvcTestResult create =
                owner.post("/api/v1/patients", "{\"fullName\":\"Terceiro\",\"birthDate\":\"1990-01-01\"}");
        assertThat(create).hasStatus(HttpStatus.CREATED);
        MvcTestResult full = owner.post("/api/v1/patients", "{\"fullName\":\"Quarto\",\"birthDate\":\"1990-01-01\"}");
        assertThat(full).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(full, "$.code")).isEqualTo("PLAN_LIMIT_REACHED");

        // Importação: sem vagas, nada entra e cada linha aparece como erro.
        MvcTestResult dry = owner.post("/api/v1/patients/import", importJson(true, "Importado A", "Importado B"));
        assertThat((Integer) json(dry, "$.ready")).isZero();
        MvcTestResult imported = owner.post("/api/v1/patients/import", importJson(false, "Importado A"));
        assertThat((Integer) json(imported, "$.created")).isZero();
        assertThat((String) json(imported, "$.issues[0].message")).contains("Limite de pacientes");

        // Reativar também ocupa vaga.
        assertThat(owner.post("/api/v1/patients/" + first + "/deactivate", "")).hasStatusOk();
        MvcTestResult partial =
                owner.post("/api/v1/patients/import", importJson(false, "Importado A", "Importado B", "Importado C"));
        assertThat((Integer) json(partial, "$.created")).isEqualTo(1); // só cabia 1
        MvcTestResult reactivate = owner.post("/api/v1/patients/" + first + "/activate", "");
        assertThat(reactivate).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(reactivate, "$.code")).isEqualTo("PLAN_LIMIT_REACHED");
    }

    // ------------------------------------------------------------------ 3. Reconvite

    @Test
    void reinvitedMemberDoesNotRegainFormerRoles() {
        String email = "reconvite" + TestData.unique() + "@teste.example";
        String password = "Senha-Nova-Forte-1";

        invite(email, "OWNER");
        accept(tokenFromLastMail(), "{\"name\":\"Pessoa\",\"password\":\"%s\"}".formatted(password));
        String membershipId = membershipOf(email);
        assertThat(owner.post("/api/v1/clinic/members/" + membershipId + "/deactivate", ""))
                .hasStatus(HttpStatus.NO_CONTENT);

        invite(email, "RECEPTION");
        accept(tokenFromLastMail(), "{\"password\":\"%s\"}".formatted(password));

        assertThat(rolesOf(email)).containsExactly("RECEPTION");
        MvcTestResult login = browser().post("/api/v1/auth/login", Browser.loginJson(email, password));
        List<String> permissions = json(login, "$.permissions");
        assertThat(permissions).doesNotContain("users:manage", "clinic:settings:manage");
    }

    @Test
    void activeMemberCannotBeInvitedAgain() {
        Person dentist = data.member(clinic, "DENTIST");
        assertThat(owner.post(
                        "/api/v1/clinic/invitations",
                        "{\"email\":\"%s\",\"roleCode\":\"FINANCE\"}".formatted(dentist.email())))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(rolesOf(dentist.email())).containsExactly("DENTIST");
    }

    private void invite(String email, String role) {
        assertThat(owner.post(
                        "/api/v1/clinic/invitations", "{\"email\":\"%s\",\"roleCode\":\"%s\"}".formatted(email, role)))
                .hasStatus(HttpStatus.CREATED);
    }

    private void accept(String token, String fields) {
        String body = "{\"token\":\"%s\",%s".formatted(token, fields.substring(1));
        assertThat(browser().post("/api/v1/public/invitations/accept", body)).hasStatus(HttpStatus.NO_CONTENT);
    }

    private String membershipOf(String email) {
        List<String> ids = json(owner.get("/api/v1/clinic/members"), "$[?(@.email=='" + email + "')].membershipId");
        return ids.getFirst();
    }

    private List<String> rolesOf(String email) {
        List<List<String>> roles = json(owner.get("/api/v1/clinic/members"), "$[?(@.email=='" + email + "')].roles");
        return roles.getFirst();
    }

    // ------------------------------------------------------------------ 4. Autoria

    @Test
    void clinicalDocumentsCanOnlyBeIssuedInTheUsersOwnName() {
        Person dentistA = data.member(clinic, "DENTIST");
        Person dentistB = data.member(clinic, "DENTIST");
        String professionalA = professionalFor(dentistA, "Dra. A");
        String professionalB = professionalFor(dentistB, "Dr. B");
        String patient = createPatient(owner, "Paciente Documento");
        Browser a = browser().login(dentistA.email(), TestData.PASSWORD);
        String url = "/api/v1/patients/" + patient + "/documents";
        String doc =
                "{\"professionalId\":\"%s\",\"type\":\"PRESCRIPTION\",\"title\":\"Receita\",\"content\":\"Uso oral\"}";

        assertThat(a.post(url, doc.formatted(professionalA))).hasStatus(HttpStatus.CREATED);
        MvcTestResult forged = a.post(url, doc.formatted(professionalB));
        assertThat(forged).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(forged, "$.code")).isEqualTo("PROFESSIONAL_MISMATCH");

        String note = "{\"content\":\"Evolução\",\"professionalId\":\"%s\"}";
        assertThat(a.post("/api/v1/patients/" + patient + "/notes", note.formatted(professionalB)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(a.post("/api/v1/patients/" + patient + "/notes", note.formatted(professionalA)))
                .hasStatus(HttpStatus.CREATED);

        // O administrador da clínica pode registrar em nome de qualquer profissional.
        assertThat(owner.post(url, doc.formatted(professionalB))).hasStatus(HttpStatus.CREATED);
    }

    private String professionalFor(Person person, String name) {
        MvcTestResult r = owner.post(
                "/api/v1/clinic/professionals",
                "{\"userId\":\"%s\",\"name\":\"%s\",\"color\":\"#9C7A5B\",\"active\":true}"
                        .formatted(person.id(), name));
        assertThat(r).hasStatus(HttpStatus.CREATED);
        return json(r, "$.id");
    }

    // ------------------------------------------------------------------ 5. Campos omitidos

    @Test
    void omittedBooleansKeepTheCurrentValueInsteadOfResettingIt() {
        MvcTestResult created = owner.post(
                "/api/v1/patients",
                "{\"fullName\":\"Consentimento\",\"birthDate\":\"1990-01-01\","
                        + "\"allowEmail\":true,\"allowSms\":true,\"allowWhatsapp\":true}");
        String id = json(created, "$.id");
        long version = ((Number) json(created, "$.version")).longValue();

        MvcTestResult updated = owner.put(
                "/api/v1/patients/" + id,
                "{\"fullName\":\"Consentimento Editado\",\"birthDate\":\"1990-01-01\",\"version\":" + version + "}");
        assertThat(updated).hasStatusOk();
        assertThat((Boolean) json(updated, "$.allowEmail")).isTrue();
        assertThat((Boolean) json(updated, "$.allowSms")).isTrue();
        assertThat((Boolean) json(updated, "$.allowWhatsapp")).isTrue();

        // Explícito continua valendo: o paciente pode revogar.
        MvcTestResult revoked = owner.put(
                "/api/v1/patients/" + id,
                "{\"fullName\":\"Consentimento Editado\",\"birthDate\":\"1990-01-01\",\"allowEmail\":false}");
        assertThat((Boolean) json(revoked, "$.allowEmail")).isFalse();
        assertThat((Boolean) json(revoked, "$.allowSms")).isTrue();

        // Cadastro novo sem a escolha: sem consentimento.
        MvcTestResult plain =
                owner.post("/api/v1/patients", "{\"fullName\":\"Sem Escolha\",\"birthDate\":\"1990-01-01\"}");
        assertThat((Boolean) json(plain, "$.allowEmail")).isFalse();
    }

    @Test
    void omittedActiveDoesNotDeactivateRecords() {
        MvcTestResult product =
                owner.post("/api/v1/inventory/products", "{\"name\":\"Produto Ativo\",\"unit\":\"un\",\"minStock\":1}");
        assertThat(product).hasStatus(HttpStatus.CREATED);
        assertThat((Boolean) json(product, "$.active")).isTrue(); // omitido em cadastro novo: nasce ativo
        String id = json(product, "$.id");

        MvcTestResult edited = owner.put(
                "/api/v1/inventory/products/" + id, "{\"name\":\"Produto Ativo\",\"unit\":\"un\",\"minStock\":2}");
        assertThat((Boolean) json(edited, "$.active")).isTrue();

        MvcTestResult off = owner.put(
                "/api/v1/inventory/products/" + id,
                "{\"name\":\"Produto Ativo\",\"unit\":\"un\",\"minStock\":2,\"active\":false}");
        assertThat((Boolean) json(off, "$.active")).isFalse();
        MvcTestResult stillOff = owner.put(
                "/api/v1/inventory/products/" + id, "{\"name\":\"Produto Ativo\",\"unit\":\"un\",\"minStock\":3}");
        assertThat((Boolean) json(stillOff, "$.active")).isFalse();

        String professional = createProfessional(owner, "Dr. Ativo");
        MvcTestResult prof = owner.put(
                "/api/v1/clinic/professionals/" + professional,
                "{\"name\":\"Dr. Ativo Editado\",\"color\":\"#9C7A5B\"}");
        assertThat(prof).hasStatusOk();
        assertThat((Boolean) json(prof, "$.active")).isTrue();
    }

    @Test
    void settingsAndSwitchesRequireTheirBooleanFields() {
        MvcTestResult settings = owner.put(
                "/api/v1/clinic/settings",
                "{\"name\":\"Clínica\",\"defaultAppointmentMinutes\":30,\"reminderHoursBefore\":24}");
        assertThat(settings).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(settings, "$.code")).isEqualTo("VALIDATION_ERROR");
    }

    // ------------------------------------------------------------------ 6. Alertas de anamnese

    @Test
    void blankAnamnesisDoesNotEraseExistingAlerts() {
        String patient = createPatient(owner, "Paciente Alerta");
        MvcTestResult forms = owner.get("/api/v1/clinic/anamnesis-forms");
        List<String> dentalForms = json(forms, "$[?(@.kind=='DENTAL')].id");
        String form = dentalForms.getFirst();
        List<java.util.Map<String, Object>> questions = json(forms, "$[?(@.id=='" + form + "')].questions[*]");
        String questionId = questions.stream()
                .filter(q -> Boolean.TRUE.equals(q.get("alertOnYes")))
                .map(q -> (String) q.get("id"))
                .findFirst()
                .orElseThrow();
        String url = "/api/v1/patients/" + patient + "/anamneses";

        assertThat(owner.post(
                        url,
                        "{\"formId\":\"%s\",\"answers\":{\"%s\":{\"answer\":\"YES\",\"detail\":\"Varfarina\"}}}"
                                .formatted(form, questionId)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(alertQuestions(patient)).contains(questionId);

        // Nova ficha em branco: a resposta anterior continua valendo.
        assertThat(owner.post(url, "{\"formId\":\"%s\",\"answers\":{}}".formatted(form)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(alertQuestions(patient)).contains(questionId);

        // Só um "NÃO" explícito resolve o alerta.
        assertThat(owner.post(
                        url,
                        "{\"formId\":\"%s\",\"answers\":{\"%s\":{\"answer\":\"NO\"}}}".formatted(form, questionId)))
                .hasStatus(HttpStatus.CREATED);
        assertThat(alertQuestions(patient)).doesNotContain(questionId);
    }

    private List<String> alertQuestions(String patient) {
        return json(owner.get("/api/v1/patients/" + patient + "/record"), "$.alerts[*].questionId");
    }
}
