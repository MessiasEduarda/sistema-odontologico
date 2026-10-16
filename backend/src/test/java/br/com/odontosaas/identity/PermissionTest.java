package br.com.odontosaas.identity;

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

/** Autorização vertical: cada papel só executa o que a matriz de permissões permite (aplicado no servidor). */
class PermissionTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    private static String invitationJson(String role) {
        return "{\"email\":\"novo" + TestData.unique() + "@teste.example\",\"roleCode\":\"" + role + "\"}";
    }

    @Test
    void receptionCannotSeeTeamNorInvite() {
        UUID clinic = data.clinic();
        Person reception = data.member(clinic, "RECEPTION");
        Browser browser = browser().login(reception.email(), TestData.PASSWORD);

        assertThat(browser.get("/api/v1/clinic/members")).hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult invite = browser.post("/api/v1/clinic/invitations", invitationJson("OWNER"));
        assertThat(invite).hasStatus(HttpStatus.FORBIDDEN);
        assertThat((String) json(invite, "$.code")).isEqualTo("FORBIDDEN");
    }

    @Test
    void dentistCanSeeTeamButCannotManageIt() {
        UUID clinic = data.clinic();
        Person dentist = data.member(clinic, "DENTIST");
        Browser browser = browser().login(dentist.email(), TestData.PASSWORD);

        assertThat(browser.get("/api/v1/clinic/members")).hasStatusOk();
        assertThat(browser.post("/api/v1/clinic/invitations", invitationJson("DENTIST")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void userWithoutActiveClinicHasNoClinicPermissions() {
        Person loner = data.user();
        Browser browser = browser().login(loner.email(), TestData.PASSWORD);

        MvcTestResult me = browser.get("/api/v1/auth/me");
        assertThat((Object) json(me, "$.activeClinic")).isNull();
        assertThat((List<?>) json(me, "$.permissions")).isEmpty();
        assertThat(browser.get("/api/v1/clinic/members")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void clinicCannotBeLeftWithoutAnOwner() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        Browser browser = browser().login(owner.email(), TestData.PASSWORD);
        UUID membership =
                superuserJdbc().queryForObject("select id from memberships where user_id = ?", UUID.class, owner.id());

        MvcTestResult result =
                browser.put("/api/v1/clinic/members/" + membership + "/roles", "{\"roleCodes\":[\"DENTIST\"]}");

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(result, "$.code")).isEqualTo("LAST_OWNER");
    }

    @Test
    void ownerCannotDeactivateThemself() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        data.member(clinic, "OWNER");
        UUID membership =
                superuserJdbc().queryForObject("select id from memberships where user_id = ?", UUID.class, owner.id());

        MvcTestResult result = browser()
                .login(owner.email(), TestData.PASSWORD)
                .post("/api/v1/clinic/members/" + membership + "/deactivate", null);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(result, "$.code")).isEqualTo("CANNOT_DEACTIVATE_SELF");
    }

    @Test
    void ownerChangesRolesAndPermissionsApplyOnNextRequest() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        Person employee = data.member(clinic, "RECEPTION");
        Browser employeeBrowser = browser().login(employee.email(), TestData.PASSWORD);
        assertThat(employeeBrowser.get("/api/v1/clinic/members")).hasStatus(HttpStatus.FORBIDDEN);

        UUID membership = superuserJdbc()
                .queryForObject("select id from memberships where user_id = ?", UUID.class, employee.id());
        assertThat(browser()
                        .login(owner.email(), TestData.PASSWORD)
                        .put(
                                "/api/v1/clinic/members/" + membership + "/roles",
                                "{\"roleCodes\":[\"DENTIST\",\"RECEPTION\"]}"))
                .hasStatusOk();

        assertThat(employeeBrowser.get("/api/v1/clinic/members")).hasStatusOk();
    }

    @Test
    void validationErrorsListTheFields() {
        UUID clinic = data.clinic();
        Person owner = data.member(clinic, "OWNER");
        MvcTestResult result = browser()
                .login(owner.email(), TestData.PASSWORD)
                .post("/api/v1/clinic/invitations", "{\"email\":\"nao-e-email\",\"roleCode\":\"\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        List<String> fields = json(result, "$.fieldErrors[*].field");
        assertThat(fields).contains("email", "roleCode");
    }
}
