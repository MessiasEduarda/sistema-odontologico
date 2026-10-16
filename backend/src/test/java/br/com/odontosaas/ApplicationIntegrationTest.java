package br.com.odontosaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.odontosaas.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void publicStatusEndpointRespondsWithoutAuthentication() {
        var result = mvc.get().uri("/api/v1/public/status").exchange();
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(result).headers().containsHeader("X-Request-Id");
    }

    @Test
    void unknownRoutesAreDeniedByDefaultWithProblemDetails() {
        assertThat(mvc.get().uri("/api/v1/patients"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void clientSuppliedRequestIdIsEchoedOnlyWhenSafe() {
        assertThat(mvc.get().uri("/api/v1/public/status").header("X-Request-Id", "abc12345-ok"))
                .hasHeader("X-Request-Id", "abc12345-ok");
        String generated = mvc.get()
                .uri("/api/v1/public/status")
                .header("X-Request-Id", "<script>")
                .exchange()
                .getResponse()
                .getHeader("X-Request-Id");
        assertThat(generated).isNotBlank().doesNotContain("<");
    }

    @Test
    void openApiDocumentIsPublished() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.paths['/api/v1/public/status']")
                .isNotNull();
    }

    @Test
    void flywayMigrationsWereApplied() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success and version = '1'", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void applicationConnectsWithRestrictedRole() {
        Boolean bypassRls = jdbc.queryForObject(
                "select rolbypassrls or rolsuper from pg_roles where rolname = current_user", Boolean.class);
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo(APP_USER);
        assertThat(bypassRls).isFalse();
    }

    @Test
    void applicationRoleCannotChangeTheSchema() {
        assertThatThrownBy(() -> jdbc.execute("create table hack (id int)"))
                .rootCause()
                .hasMessageContaining("permission denied for schema");
        assertThatThrownBy(() -> jdbc.execute("drop table tenants")).rootCause().hasMessageContaining("must be owner");
    }

    @Test
    void tenantContextFunctionReturnsNullWithoutContext() {
        assertThat(jdbc.queryForObject("select app_current_tenant_id()", String.class))
                .isNull();
    }
}
