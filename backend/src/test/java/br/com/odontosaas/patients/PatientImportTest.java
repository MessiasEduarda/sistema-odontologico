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

class PatientImportTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
    }

    private static String row(int line, String name, String birth, String cpf, String extra) {
        return ("{\"line\":%d,\"fullName\":%s,\"birthDate\":%s,\"cpf\":%s,\"mobile\":\"11999990000\","
                        + "\"email\":\"imp%s@teste.example\"%s}")
                .formatted(
                        line,
                        name == null ? "null" : "\"" + name + "\"",
                        birth == null ? "\"1990-01-01\"" : "\"" + birth + "\"",
                        "\"" + (cpf == null ? TestData.cpf() : cpf) + "\"",
                        TestData.unique(),
                        extra);
    }

    private static String body(boolean dryRun, String... rows) {
        return "{\"dryRun\":%s,\"rows\":[%s]}".formatted(dryRun, String.join(",", rows));
    }

    @Test
    void validatesThenImportsOnlyGoodRows() {
        String[] rows = {
            row(2, "Ana Importada", "02/03/1985", "529.982.247-25", ",\"sex\":\"F\",\"allowWhatsapp\":\"sim\""),
            row(3, "Bruno Importado", "1990-07-15", null, ",\"state\":\"sp\""),
            row(4, "CPF Inválido", "01/01/1980", "111.111.111-11", ""),
            row(5, "Ana Repetida", "02/03/1985", "52998224725", ""),
            row(6, "Menor Sem Responsável", "01/01/2020", null, ""),
            row(7, null, null, null, ""),
            row(8, "Data Ruim", "31/02/1990", null, "")
        };

        MvcTestResult preview = owner.post("/api/v1/patients/import", body(true, rows));
        assertThat(preview).hasStatusOk();
        assertThat((Integer) json(preview, "$.total")).isEqualTo(7);
        assertThat((Integer) json(preview, "$.ready")).isEqualTo(2);
        assertThat((Integer) json(preview, "$.created")).isZero();
        assertThat((List<Integer>) json(preview, "$.issues[*].line")).containsExactly(4, 5, 6, 7, 8);
        assertThat((List<String>) json(preview, "$.issues[?(@.line == 5)].kind"))
                .containsExactly("DUPLICATE");
        assertThat((Integer) json(owner.get("/api/v1/patients?searchTerm=importad"), "$.totalItems"))
                .isZero();

        MvcTestResult imported = owner.post("/api/v1/patients/import", body(false, rows));
        assertThat((Integer) json(imported, "$.created")).isEqualTo(2);

        MvcTestResult found = owner.get("/api/v1/patients?searchTerm=ana importada");
        assertThat((Integer) json(found, "$.totalItems")).isEqualTo(1);
        String id = json(found, "$.items[0].id");
        MvcTestResult ana = owner.get("/api/v1/patients/" + id);
        assertThat((String) json(ana, "$.sex")).isEqualTo("FEMALE");
        assertThat((Boolean) json(ana, "$.allowWhatsapp")).isTrue();
        assertThat((String) json(ana, "$.birthDate")).isEqualTo("1985-03-02");

        // Importar de novo não duplica: o CPF já existe.
        MvcTestResult again = owner.post("/api/v1/patients/import", body(false, rows[0]));
        assertThat((Integer) json(again, "$.created")).isZero();
        assertThat((List<String>) json(again, "$.issues[*].kind")).containsExactly("DUPLICATE");
    }

    @Test
    void requiresWritePermissionAndStaysInsideTheClinic() {
        Browser finance = browser().login(data.member(clinic, "FINANCE").email(), TestData.PASSWORD);
        assertThat(finance.post("/api/v1/patients/import", body(false, row(2, "X", null, null, ""))))
                .hasStatus(HttpStatus.FORBIDDEN);

        owner.post("/api/v1/patients/import", body(false, row(2, "Só Desta Clínica", null, null, "")));
        UUID other = data.clinic();
        Browser otherOwner = browser().login(data.member(other, "OWNER").email(), TestData.PASSWORD);
        assertThat((Integer) json(otherOwner.get("/api/v1/patients?searchTerm=desta clínica"), "$.totalItems"))
                .isZero();
    }
}
