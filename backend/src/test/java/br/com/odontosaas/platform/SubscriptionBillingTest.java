package br.com.odontosaas.platform;

import static br.com.odontosaas.support.Browser.json;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.subscription.dto.response.Billing;
import br.com.odontosaas.subscription.service.SubscriptionService;
import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SubscriptionBillingTest extends PostgresIntegrationTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Autowired
    TestData data;

    @Test
    void billingStateFollowsDueDateAndGracePeriod() {
        Instant now = Instant.now();
        assertThat(SubscriptionService.billing("ACTIVE", null, TODAY, now, TODAY)
                        .state())
                .isEqualTo("OK");
        assertThat(SubscriptionService.billing("ACTIVE", null, null, now, TODAY).state())
                .isEqualTo("OK");
        assertThat(SubscriptionService.billing("TRIALING", TODAY.plusDays(3), null, now, TODAY)
                        .state())
                .isEqualTo("OK");
        assertThat(SubscriptionService.billing("CANCELLED", null, TODAY.minusDays(30), now, TODAY)
                        .state())
                .isEqualTo("OK");

        Billing due = SubscriptionService.billing("ACTIVE", null, TODAY.minusDays(1), now, TODAY);
        assertThat(due.state()).isEqualTo("DUE");
        assertThat(due.dueDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(due.blockedFrom()).isEqualTo(TODAY.plusDays(SubscriptionService.GRACE_DAYS));

        int grace = SubscriptionService.GRACE_DAYS;
        assertThat(SubscriptionService.billing("ACTIVE", null, TODAY.minusDays(grace), now, TODAY)
                        .state())
                .isEqualTo("DUE");
        assertThat(SubscriptionService.billing("ACTIVE", null, TODAY.minusDays(grace + 1), now, TODAY)
                        .state())
                .isEqualTo("BLOCKED");
        assertThat(SubscriptionService.billing("TRIALING", TODAY.minusDays(grace + 1), null, now, TODAY)
                        .state())
                .isEqualTo("BLOCKED");
        // Em avaliação, mas com período pago já vencido: vale o período pago.
        assertThat(SubscriptionService.billing("TRIALING", TODAY.plusDays(30), TODAY.minusDays(grace + 1), now, TODAY)
                        .state())
                .isEqualTo("BLOCKED");
        // Marcada como inadimplente pela plataforma: vence na hora, mesmo com período ainda em vigor.
        assertThat(SubscriptionService.billing("PAST_DUE", null, TODAY.plusDays(10), now, TODAY)
                        .state())
                .isEqualTo("DUE");
    }

    @Test
    void overdueClinicIsWarnedThenBlockedUntilPaid() {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));

        // Venceu ontem: aviso, mas o sistema continua funcionando.
        assertThat(admin.put(
                        "/api/v1/platform/clinics/" + clinic + "/subscription",
                        subscription("ACTIVE", today.minusDays(1), "https://pagamento.example/clinica")))
                .hasStatusOk();
        MvcTestResult warned = owner.get("/api/v1/clinic/subscription");
        assertThat((String) json(warned, "$.billing.state")).isEqualTo("DUE");
        assertThat((String) json(warned, "$.paymentUrl")).isEqualTo("https://pagamento.example/clinica");
        assertThat(owner.get("/api/v1/patients")).hasStatusOk();

        // Tolerância esgotada: tudo bloqueado, menos a consulta da assinatura (tela de pagamento).
        admin.put(
                "/api/v1/platform/clinics/" + clinic + "/subscription",
                subscription(
                        "ACTIVE", today.minusDays(SubscriptionService.GRACE_DAYS + 1), "https://pagamento.example/x"));
        MvcTestResult blocked = owner.get("/api/v1/patients");
        assertThat(blocked).hasStatus(HttpStatus.PAYMENT_REQUIRED);
        assertThat((String) json(blocked, "$.code")).isEqualTo("SUBSCRIPTION_PAST_DUE");
        assertThat(owner.get("/api/v1/clinic/settings")).hasStatus(HttpStatus.PAYMENT_REQUIRED);
        assertThat((String) json(owner.get("/api/v1/clinic/subscription"), "$.billing.state"))
                .isEqualTo("BLOCKED");

        // Pagamento registrado pela plataforma: acesso volta.
        admin.put(
                "/api/v1/platform/clinics/" + clinic + "/subscription",
                subscription("ACTIVE", today.plusMonths(1), null));
        assertThat(owner.get("/api/v1/patients")).hasStatusOk();

        assertThat(admin.put(
                        "/api/v1/platform/clinics/" + clinic + "/subscription",
                        subscription("ACTIVE", today, "javascript:alert(1)")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static String subscription(String status, LocalDate periodEnd, String paymentUrl) {
        return """
                {"planCode":"CLINICA_PLUS","status":"%s","currentPeriodEnd":"%s","paymentUrl":%s}
                """.formatted(status, periodEnd, paymentUrl == null ? "null" : "\"" + paymentUrl + "\"");
    }
}
