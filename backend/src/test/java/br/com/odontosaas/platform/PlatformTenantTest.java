package br.com.odontosaas.platform;

import static br.com.odontosaas.support.Browser.json;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import br.com.odontosaas.support.TestData.Person;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PlatformTenantTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    private static String tenantJson(String slug, String ownerEmail) {
        return "{\"name\":\"Clínica %s\",\"slug\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(slug, slug, ownerEmail);
    }

    @Test
    void clinicOwnerIsNotAPlatformAdmin() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        Browser browser = browser().login(owner.email(), TestData.PASSWORD);

        assertThat(browser.get("/api/v1/platform/tenants")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(browser.post("/api/v1/platform/tenants", tenantJson("hack-" + TestData.unique(), "x@teste.example")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anonymousCannotReachPlatformApi() {
        assertThat(browser().get("/api/v1/platform/tenants")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminCreatesClinicWithDefaultRolesAndOwnerInvitation() {
        Person admin = data.platformAdmin();
        Browser browser = browser().login(admin.email(), TestData.PASSWORD);
        String slug = "nova-" + TestData.unique();
        String ownerEmail = "responsavel" + TestData.unique() + "@teste.example";

        MvcTestResult created = browser.post("/api/v1/platform/tenants", tenantJson(slug, ownerEmail));

        assertThat(created).hasStatus(HttpStatus.CREATED);
        UUID tenantId = UUID.fromString(json(created, "$.id"));
        List<String> roles = superuserJdbc()
                .queryForList("select code from roles where tenant_id = ? order by code", String.class, tenantId);
        assertThat(roles).containsExactly("ASSISTANT", "DENTIST", "FINANCE", "OWNER", "RECEPTION");

        // O responsável aceita o convite e vira administrador da clínica nova.
        String token = tokenFromLastMail();
        assertThat(browser()
                        .post(
                                "/api/v1/public/invitations/accept",
                                "{\"token\":\"%s\",\"name\":\"Responsável\",\"password\":\"Senha-Nova-Forte-1\"}"
                                        .formatted(token)))
                .hasStatus(HttpStatus.NO_CONTENT);
        MvcTestResult ownerLogin =
                browser().post("/api/v1/auth/login", Browser.loginJson(ownerEmail, "Senha-Nova-Forte-1"));
        assertThat((List<String>) json(ownerLogin, "$.permissions")).contains("users:manage");
    }

    @Test
    void duplicatedSlugIsRejected() {
        Person admin = data.platformAdmin();
        Browser browser = browser().login(admin.email(), TestData.PASSWORD);
        String slug = "dup-" + TestData.unique();

        assertThat(browser.post(
                        "/api/v1/platform/tenants", tenantJson(slug, "a" + TestData.unique() + "@teste.example")))
                .hasStatus(HttpStatus.CREATED);
        MvcTestResult duplicated = browser.post(
                "/api/v1/platform/tenants", tenantJson(slug.toUpperCase(), "b" + TestData.unique() + "@teste.example"));
        assertThat(duplicated).hasStatus(HttpStatus.BAD_REQUEST); // maiúsculas violam o formato do slug
        MvcTestResult conflict =
                browser.post("/api/v1/platform/tenants", tenantJson(slug, "c" + TestData.unique() + "@teste.example"));
        assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
        assertThat((String) json(conflict, "$.code")).isEqualTo("SLUG_TAKEN");
    }

    @Test
    void platformAdminDoesNotGetClinicPermissions() {
        Person admin = data.platformAdmin();
        Browser browser = browser().login(admin.email(), TestData.PASSWORD);

        assertThat((Boolean) json(browser.get("/api/v1/auth/me"), "$.platformAdmin"))
                .isTrue();
        assertThat(browser.get("/api/v1/clinic/members")).hasStatus(HttpStatus.FORBIDDEN);
    }
}
