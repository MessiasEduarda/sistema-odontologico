package br.com.odontosaas.finance;

import static br.com.odontosaas.support.Browser.json;
import static br.com.odontosaas.support.Fixtures.createPatient;
import static br.com.odontosaas.support.Fixtures.createProcedure;
import static br.com.odontosaas.support.Fixtures.createProfessional;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class TreatmentFinanceFlowTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinic;
    Browser owner;
    String professional;
    String patient;
    String procedure;

    @BeforeEach
    void setUp() {
        clinic = data.clinic();
        owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        professional = createProfessional(owner, "Dra. Orçamento");
        patient = createPatient(owner, "Paciente Financeiro");
        procedure = createProcedure(owner, "Restauração resina", "250.00");
    }

    private String planJson(String discount) {
        return ("{\"patientId\":\"%s\",\"professionalId\":\"%s\",\"title\":\"Tratamento completo\",\"discount\":%s,"
                        + "\"items\":[{\"procedureId\":\"%s\",\"description\":\"Restauração 36\",\"tooth\":36,"
                        + "\"quantity\":2,\"unitPrice\":250.00,\"discount\":50.00},"
                        + "{\"description\":\"Limpeza\",\"quantity\":1,\"unitPrice\":150.00}]}")
                .formatted(patient, professional, discount, procedure);
    }

    @Test
    void totalsAreComputedByTheServer() {
        MvcTestResult plan = owner.post("/api/v1/treatment-plans", planJson("100.00"));
        assertThat(plan).hasStatus(HttpStatus.CREATED);
        assertThat(new BigDecimal(json(plan, "$.subtotal").toString())).isEqualByComparingTo("650.00");
        assertThat(new BigDecimal(json(plan, "$.itemsDiscount").toString())).isEqualByComparingTo("50.00");
        assertThat(new BigDecimal(json(plan, "$.total").toString())).isEqualByComparingTo("500.00");

        MvcTestResult tooMuch = owner.post("/api/v1/treatment-plans", planJson("9999.00"));
        assertThat(tooMuch).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void approvalGeneratesInstallmentsAndPaymentsFlow() {
        String plan = json(owner.post("/api/v1/treatment-plans", planJson("0")), "$.id"); // total 600,00
        LocalDate first = LocalDate.now().plusDays(1);
        MvcTestResult approved = owner.post(
                "/api/v1/treatment-plans/" + plan + "/approve",
                "{\"approvedByName\":\"Paciente Financeiro\",\"generateReceivable\":true,\"installments\":3,"
                        + "\"firstDueDate\":\"" + first + "\"}");
        assertThat(approved).hasStatusOk();
        String receivable = json(approved, "$.receivableId");
        assertThat(receivable).isNotNull();

        // Plano aprovado não pode mais ser editado.
        assertThat(owner.put("/api/v1/treatment-plans/" + plan, planJson("0")))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        MvcTestResult r = owner.get("/api/v1/finance/receivables/" + receivable);
        List<Object> amounts = json(r, "$.installments[*].amount");
        assertThat(amounts).hasSize(3);
        assertThat(amounts.stream().map(a -> new BigDecimal(a.toString())).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("600.00");
        String firstInstallment = json(r, "$.installments[0].id");

        MvcTestResult over = owner.post(
                "/api/v1/finance/installments/" + firstInstallment + "/payments",
                "{\"amount\":999.00,\"method\":\"PIX\"}");
        assertThat(over).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult partial = owner.post(
                "/api/v1/finance/installments/" + firstInstallment + "/payments",
                "{\"amount\":50.00,\"method\":\"PIX\"}");
        assertThat(partial).hasStatusOk();
        assertThat((String) json(partial, "$.installments[0].status")).isEqualTo("OPEN");
        MvcTestResult paid = owner.post(
                "/api/v1/finance/installments/" + firstInstallment + "/payments",
                "{\"amount\":150.00,\"method\":\"CREDIT_CARD\"}");
        assertThat((String) json(paid, "$.installments[0].status")).isEqualTo("PAID");

        String payment = json(paid, "$.installments[0].payments[1].id");
        MvcTestResult refunded = owner.post(
                "/api/v1/finance/payments/" + payment + "/refunds", "{\"amount\":30.00,\"reason\":\"Ajuste\"}");
        assertThat(refunded).hasStatusOk();
        assertThat((String) json(refunded, "$.installments[0].status")).isEqualTo("OPEN");
        assertThat(new BigDecimal(json(refunded, "$.paid").toString())).isEqualByComparingTo("170.00");

        // Não cancela com recebimentos.
        MvcTestResult cancel =
                owner.post("/api/v1/finance/receivables/" + receivable + "/cancel", "{\"reason\":\"Desistência\"}");
        assertThat(cancel).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        MvcTestResult summary = owner.get("/api/v1/finance/summary");
        assertThat(new BigDecimal(json(summary, "$.receivedThisMonth").toString()))
                .isEqualByComparingTo("170.00");

        LocalDate today = LocalDate.now();
        MvcTestResult flow = owner.get("/api/v1/finance/cash-flow?from=%s&to=%s".formatted(today.minusDays(1), today));
        assertThat(new BigDecimal(json(flow, "$.net").toString())).isEqualByComparingTo("170.00");
    }

    @Test
    void performingAllItemsCompletesThePlan() {
        String plan = json(owner.post("/api/v1/treatment-plans", planJson("0")), "$.id");
        owner.post(
                "/api/v1/treatment-plans/" + plan + "/approve",
                "{\"approvedByName\":\"P\",\"generateReceivable\":false,\"installments\":1}");
        List<String> items = json(owner.get("/api/v1/treatment-plans/" + plan), "$.items[*].id");

        for (String item : items) {
            assertThat(owner.post(
                            "/api/v1/patients/" + patient + "/performed-procedures",
                            "{\"professionalId\":\"%s\",\"description\":\"Feito\",\"treatmentItemId\":\"%s\"}"
                                    .formatted(professional, item)))
                    .hasStatus(HttpStatus.CREATED);
        }
        assertThat((String) json(owner.get("/api/v1/treatment-plans/" + plan), "$.status"))
                .isEqualTo("COMPLETED");

        // Item já realizado não pode ser baixado de novo.
        assertThat(owner.post(
                        "/api/v1/patients/" + patient + "/performed-procedures",
                        "{\"professionalId\":\"%s\",\"description\":\"De novo\",\"treatmentItemId\":\"%s\"}"
                                .formatted(professional, items.getFirst())))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void payablesAndSuppliers() {
        String supplier =
                json(owner.post("/api/v1/suppliers", "{\"name\":\"Dental Fornecedor\",\"active\":true}"), "$.id");
        MvcTestResult payable = owner.post(
                "/api/v1/finance/payables",
                "{\"supplierId\":\"%s\",\"description\":\"Resinas\",\"category\":\"MATERIAIS\",\"amount\":320.50,\"dueDate\":\"%s\"}"
                        .formatted(supplier, LocalDate.now()));
        assertThat(payable).hasStatus(HttpStatus.CREATED);
        String id = json(payable, "$.id");
        assertThat((String)
                        json(owner.post("/api/v1/finance/payables/" + id + "/pay", "{\"method\":\"PIX\"}"), "$.status"))
                .isEqualTo("PAID");
        assertThat(owner.post("/api/v1/finance/payables/" + id + "/cancel", "{\"reason\":\"x\"}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void dentistCannotSeeFinance() {
        Browser dentist = browser().login(data.member(clinic, "DENTIST").email(), TestData.PASSWORD);
        assertThat(dentist.get("/api/v1/finance/summary")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(dentist.get("/api/v1/finance/receivables")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherClinicCannotPayOrSeeReceivables() {
        String plan = json(owner.post("/api/v1/treatment-plans", planJson("0")), "$.id");
        String receivable = json(
                owner.post(
                        "/api/v1/treatment-plans/" + plan + "/approve",
                        "{\"approvedByName\":\"P\",\"generateReceivable\":true,\"installments\":1}"),
                "$.receivableId");
        String installment = json(owner.get("/api/v1/finance/receivables/" + receivable), "$.installments[0].id");

        Browser intruder = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        assertThat(intruder.get("/api/v1/finance/receivables/" + receivable)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(intruder.post(
                        "/api/v1/finance/installments/" + installment + "/payments",
                        "{\"amount\":1,\"method\":\"CASH\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(intruder.get("/api/v1/treatment-plans/" + plan)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void installmentSplitKeepsCents() {
        List<BigDecimal> parts = FinanceService.split(new BigDecimal("100.00"), 3);
        assertThat(parts).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
    }
}
