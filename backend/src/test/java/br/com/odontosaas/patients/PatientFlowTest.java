package br.com.odontosaas.patients;

import static br.com.odontosaas.support.Browser.json;
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

class PatientFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
    }

    private static String patientJson(String name, String cpf, String birth) {
        return ("{\"fullName\":\"%s\",\"cpf\":%s,\"birthDate\":\"%s\",\"mobile\":\"(11) 98888-7777\","
                        + "\"email\":\"p%s@teste.example\",\"allowEmail\":true}")
                .formatted(name, "\"" + (cpf == null ? TestData.cpf() : cpf) + "\"", birth, TestData.unique());
    }

    @Test
    void createSearchAndUpdateWithOptimisticLocking() {
        MvcTestResult created =
                owner.post("/api/v1/patients", patientJson("Joana Fictícia", "529.982.247-25", "1985-03-02"));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = json(created, "$.id");
        assertThat((String) json(created, "$.cpf")).isEqualTo("52998224725");

        assertThat((List<String>) json(owner.get("/api/v1/patients?q=joana"), "$.items[*].id"))
                .containsExactly(id);
        assertThat((List<String>) json(owner.get("/api/v1/patients?q=52998"), "$.items[*].id"))
                .containsExactly(id);
        assertThat((List<String>) json(owner.get("/api/v1/patients?q=98888"), "$.items[*].id"))
                .containsExactly(id);

        String update = patientJson("Joana Fictícia Silva", "529.982.247-25", "1985-03-02")
                .replace("}", ",\"version\":0}");
        assertThat(owner.put("/api/v1/patients/" + id, update)).hasStatusOk();
        MvcTestResult stale = owner.put("/api/v1/patients/" + id, update);
        assertThat(stale).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(stale, "$.code")).isEqualTo("STALE_VERSION");
    }

    @Test
    void invalidAndDuplicatedCpfAreRejected() {
        MvcTestResult invalid =
                owner.post("/api/v1/patients", patientJson("CPF Inválido", "111.111.111-11", "1990-01-01"));
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(invalid, "$.fieldErrors[0].field")).isEqualTo("cpf");

        assertThat(owner.post("/api/v1/patients", patientJson("Primeiro", "168.995.350-09", "1990-01-01")))
                .hasStatus(HttpStatus.CREATED);
        MvcTestResult duplicated = owner.post("/api/v1/patients", patientJson("Segundo", "16899535009", "1990-01-01"));
        assertThat(duplicated).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(duplicated, "$.code")).isEqualTo("DUPLICATE_CPF");

        // O mesmo CPF pode existir em OUTRA clínica (cadastros independentes).
        Browser other = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(other.post("/api/v1/patients", patientJson("Outra Clínica", "16899535009", "1990-01-01")))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void minorRequiresGuardian() {
        MvcTestResult result = owner.post("/api/v1/patients", patientJson("Criança", null, "2020-06-01"));
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(result, "$.fieldErrors[0].field")).isEqualTo("guardianName");
    }

    @Test
    void financeRoleCanReadButNotWrite() {
        Browser finance = browser().login(data.member(clinic, "FINANCE").email(), TestData.PASSWORD);
        assertThat(finance.get("/api/v1/patients")).hasStatusOk();
        assertThat(finance.post("/api/v1/patients", patientJson("Sem permissão", null, "1990-01-01")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
