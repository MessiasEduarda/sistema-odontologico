package br.com.odontosaas.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Garantias do BANCO, independentes do código da aplicação: mesmo uma consulta sem filtro de clínica (bug, SQL
 * nativo esquecido) não enxerga nem grava dados de outra clínica.
 */
class RowLevelSecurityTest extends PostgresIntegrationTest {

    /** Tabelas com tenant_id que são intencionalmente globais. Toda exceção precisa de justificativa documentada. */
    /**
     * subscriptions: dado comercial da PLATAFORMA (plano/cobrança da clínica), gerido só pela administração da
     * plataforma; não contém dados de pacientes. Leituras da própria clínica filtram por tenant explicitamente.
     */
    private static final Set<String> GLOBAL_TABLES_WITH_TENANT_COLUMN = Set.of("subscriptions");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestData data;

    @Test
    void everyTableWithTenantIdHasForcedRowLevelSecurity() {
        List<String> unprotected = superuserJdbc().queryForList("""
                        select c.relname from pg_class c
                        join pg_namespace n on n.oid = c.relnamespace
                        join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped
                        where n.nspname = 'odonto' and c.relkind = 'r'
                          and not (c.relrowsecurity and c.relforcerowsecurity)
                        """, String.class);
        assertThat(unprotected).isSubsetOf(GLOBAL_TABLES_WITH_TENANT_COLUMN);
    }

    @Test
    void withoutContextNoClinicRowIsVisible() {
        data.clinic();
        assertThat(jdbc.queryForObject("select count(*) from roles", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from role_permissions", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from memberships", Integer.class))
                .isZero();
    }

    @Test
    void contextExposesOnlyTheActiveClinic() {
        UUID clinicA = data.clinic();
        UUID clinicB = data.clinic();

        List<UUID> visibleTenants = TenantContext.runAs(
                clinicA, null, () -> jdbc.queryForList("select distinct tenant_id from roles", UUID.class));

        assertThat(visibleTenants).containsExactly(clinicA).doesNotContain(clinicB);
    }

    @Test
    void cannotInsertRowsIntoAnotherClinic() {
        UUID clinicA = data.clinic();
        UUID clinicB = data.clinic();

        assertThatThrownBy(() -> TenantContext.runAs(
                        clinicA,
                        null,
                        () -> jdbc.update(
                                "insert into roles (id, tenant_id, code, name) values (?, ?, 'HACK', 'Hack')",
                                UUID.randomUUID(),
                                clinicB)))
                .rootCause()
                .hasMessageContaining("row-level security");
    }

    @Test
    void cannotMoveRowsToAnotherClinic() {
        UUID clinicA = data.clinic();
        UUID clinicB = data.clinic();

        assertThatThrownBy(() -> TenantContext.runAs(
                        clinicA,
                        null,
                        () -> jdbc.update("update roles set tenant_id = ? where tenant_id = ?", clinicB, clinicA)))
                .rootCause()
                .hasMessageContaining("row-level security");
    }

    @Test
    void contextDoesNotLeakBetweenPooledConnections() {
        UUID clinic = data.clinic();
        TenantContext.runAs(clinic, null, () -> jdbc.queryForObject("select count(*) from roles", Integer.class));

        // Sem contexto, as próximas conexões do pool não podem carregar o tenant anterior.
        for (int i = 0; i < 20; i++) {
            assertThat(jdbc.queryForObject("select app_current_tenant_id()", UUID.class))
                    .isNull();
        }
    }

    @Test
    void auditTrailIsAppendOnlyForTheApplication() {
        UUID clinic = data.clinic();
        TenantContext.runAs(
                clinic,
                null,
                () -> jdbc.update(
                        "insert into audit_events (id, tenant_id, action) values (?, ?, 'TEST')",
                        UUID.randomUUID(),
                        clinic));

        assertThatThrownBy(() -> TenantContext.runAs(
                        clinic,
                        null,
                        () -> jdbc.update("update audit_events set action = 'X' where tenant_id = ?", clinic)))
                .rootCause()
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.runAs(
                        clinic, null, () -> jdbc.update("delete from audit_events where tenant_id = ?", clinic)))
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    void crossClinicReferencesAreRejectedByCompositeForeignKeys() {
        UUID clinicA = data.clinic();
        UUID clinicB = data.clinic();
        TestData.Person person = data.member(clinicA, "OWNER");
        UUID membershipA =
                superuserJdbc().queryForObject("select id from memberships where tenant_id = ?", UUID.class, clinicA);
        UUID roleInB = superuserJdbc()
                .queryForObject("select id from roles where tenant_id = ? and code = 'OWNER'", UUID.class, clinicB);

        // Mesmo um superusuário (que ignora RLS) não consegue ligar um vínculo da clínica A a um papel da B.
        assertThatThrownBy(() -> superuserJdbc()
                        .update(
                                "insert into membership_roles (tenant_id, membership_id, role_id) values (?, ?, ?)",
                                clinicA,
                                membershipA,
                                roleInB))
                .rootCause()
                .hasMessageContaining("foreign key");
        assertThat(person).isNotNull();
    }
}
