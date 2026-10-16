package br.com.odontosaas.tenancy;

import static br.com.odontosaas.support.Browser.json;
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
 * Tentativas de acesso cruzado entre clínicas pela API, inclusive com IDs conhecidos de outra clínica. A resposta
 * esperada é sempre 404 (não revela a existência) e nenhum efeito colateral.
 */
class TenantIsolationTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    UUID clinicA;
    UUID clinicB;
    Person ownerA;
    Person ownerB;
    Person dentistB;

    @BeforeEach
    void setUp() {
        clinicA = data.clinic();
        clinicB = data.clinic();
        ownerA = data.member(clinicA, "OWNER");
        ownerB = data.member(clinicB, "OWNER");
        dentistB = data.member(clinicB, "DENTIST");
    }

    @Test
    void membersListShowsOnlyTheActiveClinic() {
        MvcTestResult result =
                browser().login(ownerA.email(), TestData.PASSWORD).get("/api/v1/clinic/members");

        assertThat(result).hasStatusOk();
        List<String> emails = json(result, "$[*].email");
        assertThat(emails).containsExactly(ownerA.email());
    }

    @Test
    void cannotChangeRolesOfAnotherClinicsMemberByKnownId() {
        UUID membershipInB = membershipId(ownerB, clinicB);
        Browser attacker = browser().login(ownerA.email(), TestData.PASSWORD);

        MvcTestResult result =
                attacker.put("/api/v1/clinic/members/" + membershipInB + "/roles", "{\"roleCodes\":[\"RECEPTION\"]}");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(roleCodes(membershipInB)).containsExactly("OWNER");
    }

    @Test
    void cannotDeactivateAnotherClinicsMemberByKnownId() {
        UUID membershipInB = membershipId(dentistB, clinicB);
        Browser attacker = browser().login(ownerA.email(), TestData.PASSWORD);

        assertThat(attacker.post("/api/v1/clinic/members/" + membershipInB + "/deactivate", null))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(superuserJdbc()
                        .queryForObject("select status from memberships where id = ?", String.class, membershipInB))
                .isEqualTo("ACTIVE");
    }

    @Test
    void cannotRevokeAnotherClinicsInvitationByKnownId() {
        Browser ownerOfB = browser().login(ownerB.email(), TestData.PASSWORD);
        MvcTestResult created = ownerOfB.post(
                "/api/v1/clinic/invitations",
                "{\"email\":\"convidado" + TestData.unique() + "@teste.example\",\"roleCode\":\"DENTIST\"}");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String invitationInB = json(created, "$.id");

        Browser attacker = browser().login(ownerA.email(), TestData.PASSWORD);
        assertThat(attacker.delete("/api/v1/clinic/invitations/" + invitationInB))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(superuserJdbc()
                        .queryForObject(
                                "select status from invitations where id = ?::uuid", String.class, invitationInB))
                .isEqualTo("PENDING");
    }

    /**
     * Caso sutil: a pessoa tem vínculo nas DUAS clínicas. O RLS de memberships deixa o usuário ver os próprios
     * vínculos (para listar suas clínicas), então a aplicação também precisa filtrar pela clínica ativa.
     */
    @Test
    void userWithAccessToBothClinicsCannotActOnClinicBWhileClinicAIsActive() {
        Person both = data.member(clinicA, "OWNER");
        data.addMember(clinicB, both, "RECEPTION");
        UUID ownMembershipInB = membershipId(both, clinicB);

        Browser browser = browser().login(both.email(), TestData.PASSWORD).switchClinic(clinicA);

        // Com a clínica A ativa, o vínculo na B (onde ele é só recepção) não pode ser promovido a OWNER.
        assertThat(browser.put("/api/v1/clinic/members/" + ownMembershipInB + "/roles", "{\"roleCodes\":[\"OWNER\"]}"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(roleCodes(ownMembershipInB)).containsExactly("RECEPTION");

        List<String> emails = json(browser.get("/api/v1/clinic/members"), "$[*].email");
        assertThat(emails).doesNotContain(ownerB.email(), dentistB.email());
    }

    @Test
    void cannotSwitchToAClinicWithoutMembership() {
        Browser browser = browser().login(ownerA.email(), TestData.PASSWORD);

        MvcTestResult result = browser.post("/api/v1/auth/switch-clinic", "{\"clinicId\":\"" + clinicB + "\"}");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((String) json(browser.get("/api/v1/auth/me"), "$.activeClinic.id"))
                .isEqualTo(clinicA.toString());
    }

    @Test
    void tenantIdSentByTheClientIsIgnored() {
        Browser browser = browser().login(ownerA.email(), TestData.PASSWORD);

        // Parâmetros/headers extras com o ID da clínica B não mudam o contexto, que vem só da sessão.
        MvcTestResult result = browser.get("/api/v1/clinic/members?tenantId=" + clinicB + "&clinicId=" + clinicB);

        List<String> emails = json(result, "$[*].email");
        assertThat(emails).containsExactly(ownerA.email());
    }

    @Test
    void deactivatedMemberLosesAccessImmediately() {
        Person dentistA = data.member(clinicA, "DENTIST");
        Browser victim = browser().login(dentistA.email(), TestData.PASSWORD);
        assertThat(victim.get("/api/v1/auth/me")).hasStatusOk();

        Browser owner = browser().login(ownerA.email(), TestData.PASSWORD);
        UUID membership = membershipId(dentistA, clinicA);
        assertThat(owner.post("/api/v1/clinic/members/" + membership + "/deactivate", null))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(victim.get("/api/v1/clinic/members")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void switchingClinicRotatesTheSession() {
        Person both = data.member(clinicA, "OWNER");
        data.addMember(clinicB, both, "DENTIST");
        Browser browser = browser().login(both.email(), TestData.PASSWORD);
        String before = browser.session();

        browser.switchClinic(clinicB);

        assertThat(browser.session()).isNotEqualTo(before);
        assertThat(browser().useSession(before).get("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        List<String> permissions = json(browser.get("/api/v1/auth/me"), "$.permissions");
        assertThat(permissions).contains("clinical_records:write").doesNotContain("users:manage");
    }

    private UUID membershipId(Person person, UUID clinic) {
        return superuserJdbc()
                .queryForObject(
                        "select id from memberships where user_id = ? and tenant_id = ?",
                        UUID.class,
                        person.id(),
                        clinic);
    }

    private List<String> roleCodes(UUID membershipId) {
        return superuserJdbc()
                .queryForList(
                        "select r.code from membership_roles mr join roles r on r.id = mr.role_id where mr.membership_id = ?",
                        String.class,
                        membershipId);
    }
}
