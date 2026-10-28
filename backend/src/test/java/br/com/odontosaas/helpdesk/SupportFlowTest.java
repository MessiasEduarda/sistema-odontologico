package br.com.odontosaas.helpdesk;

import static br.com.odontosaas.support.Browser.json;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.helpdesk.job.SupportJobs;
import br.com.odontosaas.helpdesk.service.SupportService;
import br.com.odontosaas.subscription.service.SubscriptionService;
import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class SupportFlowTest extends PostgresIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};

    @Autowired
    TestData data;

    @Autowired
    SupportJobs jobs;

    @Test
    void clinicOpensTicketAndSupportAnswersWithoutShowingInternalNotes() {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);

        MvcTestResult opened = owner.post("/api/v1/support/tickets", ticketJson("BLOCKING", "abc123trace"));
        assertThat(opened).hasStatus(HttpStatus.CREATED);
        String id = json(opened, "$.id");
        assertThat((String) json(opened, "$.protocol")).matches("SUP-\\d{4}-\\d{5}");
        assertThat((String) json(opened, "$.priority")).isEqualTo("URGENT");
        assertThat((String) json(opened, "$.status")).isEqualTo("OPEN");
        assertThat((String) json(opened, "$.traceId")).isEqualTo("abc123trace");
        assertThat(pendingNotifications(id)).isPositive();

        assertThat(body(admin.get("/api/v1/platform/support/tickets?q=" + json(opened, "$.protocol"))))
                .contains(id);
        assertThat(admin.post(
                        "/api/v1/platform/support/tickets/" + id + "/messages",
                        "{\"body\":\"Nota só da equipe\",\"internal\":true}"))
                .hasStatusOk();
        MvcTestResult replied = admin.post(
                "/api/v1/platform/support/tickets/" + id + "/messages", "{\"body\":\"Já estamos verificando.\"}");
        assertThat(replied).hasStatusOk();
        assertThat((String) json(replied, "$.status")).isEqualTo("WAITING_CLINIC");
        assertThat((String) json(replied, "$.firstResponseAt")).isNotNull();
        assertThat(body(replied)).contains("Nota só da equipe");
        admin.post("/api/v1/platform/support/tickets/" + id + "/messages", "{\"body\":\"Pode testar de novo?\"}");

        assertThat((Integer) json(owner.get("/api/v1/support/unread"), "$.count"))
                .isEqualTo(2);
        assertThat((Integer) json(owner.get("/api/v1/support/tickets"), "$[0].unreadCount"))
                .isEqualTo(2);
        MvcTestResult seen = owner.get("/api/v1/support/tickets/" + id);
        assertThat(body(seen)).contains("Já estamos verificando.").doesNotContain("Nota só da equipe");
        assertThat((Integer) json(owner.get("/api/v1/support/unread"), "$.count"))
                .isZero();

        int supportUnreadBefore = json(admin.get("/api/v1/platform/support/unread"), "$.count");
        MvcTestResult answer = owner.post("/api/v1/support/tickets/" + id + "/messages", "{\"body\":\"Obrigada!\"}");
        assertThat((String) json(answer, "$.status")).isEqualTo("IN_PROGRESS");
        owner.post("/api/v1/support/tickets/" + id + "/messages", "{\"body\":\"Funcionou.\"}");
        assertThat((Integer) json(admin.get("/api/v1/platform/support/unread"), "$.count"))
                .isEqualTo(supportUnreadBefore + 2);
        admin.get("/api/v1/platform/support/tickets/" + id);
        assertThat((Integer) json(admin.get("/api/v1/platform/support/unread"), "$.count"))
                .isEqualTo(supportUnreadBefore);
    }

    @Test
    void teamMemberSeesOnlyOwnTicketsAndOwnerSeesTheWholeClinic() {
        UUID clinic = data.clinic();
        Browser dentist = browser().login(data.member(clinic, "DENTIST").email(), TestData.PASSWORD);
        Browser reception = browser().login(data.member(clinic, "RECEPTION").email(), TestData.PASSWORD);
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        Browser otherClinic =
                browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);

        String id = json(dentist.post("/api/v1/support/tickets", ticketJson("QUESTION", null)), "$.id");

        assertThat(reception.get("/api/v1/support/tickets/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(body(reception.get("/api/v1/support/tickets"))).doesNotContain(id);
        assertThat(body(owner.get("/api/v1/support/tickets"))).contains(id);
        assertThat(owner.get("/api/v1/support/tickets/" + id)).hasStatusOk();
        assertThat(otherClinic.get("/api/v1/support/tickets/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(otherClinic.post("/api/v1/support/tickets/" + id + "/messages", "{\"body\":\"oi\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(dentist.get("/api/v1/platform/support/tickets")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void resolvedTicketReopensOnReplyAndClosedTicketDoesNot() {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        String id = json(owner.post("/api/v1/support/tickets", ticketJson("DEGRADED", null)), "$.id");

        assertThat(owner.post("/api/v1/support/tickets/" + id + "/rating", "{\"rating\":5}"))
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        admin.post("/api/v1/platform/support/tickets/" + id + "/status", "{\"status\":\"RESOLVED\"}");
        MvcTestResult rated =
                owner.post("/api/v1/support/tickets/" + id + "/rating", "{\"rating\":5,\"comment\":\"Ótimo\"}");
        assertThat((Integer) json(rated, "$.rating")).isEqualTo(5);

        MvcTestResult reopened =
                owner.post("/api/v1/support/tickets/" + id + "/messages", "{\"body\":\"Voltou a acontecer\"}");
        assertThat((String) json(reopened, "$.status")).isEqualTo("IN_PROGRESS");

        admin.post("/api/v1/platform/support/tickets/" + id + "/status", "{\"status\":\"CLOSED\"}");
        MvcTestResult refused = owner.post("/api/v1/support/tickets/" + id + "/messages", "{\"body\":\"De novo\"}");
        assertThat(refused).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(refused, "$.code")).isEqualTo("TICKET_CLOSED");
    }

    @Test
    void clinicCannotFloodTheQueue() {
        Browser owner = browser().login(data.member(data.clinic(), "OWNER").email(), TestData.PASSWORD);
        for (int i = 0; i < SupportService.MAX_TICKETS_PER_DAY; i++) {
            assertThat(owner.post("/api/v1/support/tickets", ticketJson("SUGGESTION", null)))
                    .hasStatus(HttpStatus.CREATED);
        }
        MvcTestResult refused = owner.post("/api/v1/support/tickets", ticketJson("SUGGESTION", null));
        assertThat(refused).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) json(refused, "$.code")).isEqualTo("SUPPORT_TICKET_LIMIT");
    }

    @Test
    void blockedClinicStillReachesSupport() {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        admin.put("/api/v1/platform/clinics/" + clinic + "/subscription", """
                {"planCode":"CLINICA_PLUS","status":"ACTIVE","currentPeriodEnd":"%s","paymentUrl":null}
                """.formatted(
                        today.minusDays(SubscriptionService.GRACE_DAYS + 1)));

        assertThat(owner.get("/api/v1/patients")).hasStatus(HttpStatus.PAYMENT_REQUIRED);
        assertThat(owner.get("/api/v1/support/tickets")).hasStatusOk();
        assertThat(owner.post("/api/v1/support/tickets", ticketJson("BLOCKING", null)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void attachmentsAcceptImagesOnlyAndAreDownloadedBySupport() {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        String id = json(owner.post("/api/v1/support/tickets", ticketJson("BLOCKING", null)), "$.id");

        MvcTestResult ok = upload(owner, id, "print.png", PNG);
        assertThat(ok).hasStatusOk();
        String attachmentId = json(ok, "$.attachments[0].id");
        assertThat(upload(owner, id, "pagina.html", "<html>".getBytes())).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(supportUnread(id)).isEqualTo(1);

        assertThat(admin.get("/api/v1/platform/support/tickets/" + id + "/attachments/" + attachmentId + "/download"))
                .hasStatusOk();
        Integer audited = superuserJdbc()
                .queryForObject(
                        "select count(*) from odonto.audit_events where action = 'SUPPORT_ATTACHMENT_VIEWED'"
                                + " and resource_id = ?",
                        Integer.class,
                        attachmentId);
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void routineRemindsResolvesAndClosesIdleTickets() {
        UUID clinic = data.clinic();
        Browser owner = browser().login(data.member(clinic, "OWNER").email(), TestData.PASSWORD);
        Browser admin = browser().login(data.platformAdmin().email(), TestData.PASSWORD);
        String id = json(owner.post("/api/v1/support/tickets", ticketJson("QUESTION", null)), "$.id");
        admin.post("/api/v1/platform/support/tickets/" + id + "/messages", "{\"body\":\"Pode enviar um print?\"}");

        Instant now = Instant.now();
        jobs.maintenance(now.plus(Duration.ofDays(6)));
        assertThat(body(owner.get("/api/v1/support/tickets/" + id))).contains("Lembrete enviado");

        jobs.maintenance(now.plus(Duration.ofDays(12)));
        assertThat((String) json(owner.get("/api/v1/support/tickets/" + id), "$.status"))
                .isEqualTo("RESOLVED");

        jobs.maintenance(now.plus(Duration.ofDays(20)));
        assertThat((String) json(owner.get("/api/v1/support/tickets/" + id), "$.status"))
                .isEqualTo("CLOSED");
    }

    @Test
    void contactIsPublic() {
        MvcTestResult contact = browser().get("/api/v1/public/support/contact");
        assertThat(contact).hasStatusOk();
        assertThat((String) json(contact, "$.hours")).contains("segunda a sexta");
    }

    private int pendingNotifications(String ticketId) {
        List<Integer> count = superuserJdbc()
                .queryForList(
                        "select count(*) from odonto.support_notifications where ticket_id = ?::uuid",
                        Integer.class,
                        ticketId);
        return count.getFirst();
    }

    private int supportUnread(String ticketId) {
        return superuserJdbc()
                .queryForObject(
                        "select support_unread from odonto.support_tickets where id = ?::uuid",
                        Integer.class,
                        ticketId);
    }

    private MvcTestResult upload(Browser who, String ticketId, String name, byte[] content) {
        String csrf = "csrf-upload";
        return mvc.post()
                .uri("/api/v1/support/tickets/" + ticketId + "/attachments")
                .multipart()
                .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .cookie(new Cookie("XSRF-TOKEN", csrf), new Cookie(Browser.SESSION_COOKIE, who.session()))
                .header("X-XSRF-TOKEN", csrf)
                .exchange();
    }

    private static String ticketJson(String impact, String traceId) {
        return """
                {"subject":"Agenda não abre","category":"BUG","impact":"%s",
                 "description":"Ao abrir a agenda aparece uma mensagem de erro.","page":"/agenda","traceId":%s}
                """.formatted(impact, traceId == null ? "null" : "\"" + traceId + "\"");
    }

    private static String body(MvcTestResult result) {
        return Browser.body(result);
    }
}
