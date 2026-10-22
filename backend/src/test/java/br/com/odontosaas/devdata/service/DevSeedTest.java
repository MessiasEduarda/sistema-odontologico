package br.com.odontosaas.devdata.service;

import static br.com.odontosaas.support.Browser.json;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.support.Browser;
import br.com.odontosaas.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * O seed de desenvolvimento (módulo devdata) só existe no perfil dev: cria as duas clínicas e os usuários fictícios, e
 * roda depois do bootstrap do administrador da plataforma.
 */
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"app.seed.enabled=true", "app.seed.password=Seed-De-Teste-123"})
class DevSeedTest extends PostgresIntegrationTest {

    @Autowired
    List<ApplicationRunner> runners;

    @Test
    void seedCreatesDemoClinicsAndUsers() {
        Browser admin = browser();
        MvcTestResult login =
                admin.post("/api/v1/auth/login", Browser.loginJson("admin@odonto.example", "Seed-De-Teste-123"));
        assertThat(login).hasStatusOk();
        assertThat((Boolean) json(login, "$.platformAdmin")).isTrue();

        Browser owner = browser();
        assertThat(owner.post("/api/v1/auth/login", Browser.loginJson("dona@sorriso.example", "Seed-De-Teste-123")))
                .hasStatusOk();
        assertThat(owner.get("/api/v1/clinic/subscription")).hasStatusOk();
        assertThat((Integer) json(owner.get("/api/v1/patients"), "$.totalItems"))
                .isGreaterThan(0);
    }

    @Test
    void bootstrapRunsBeforeTheDevSeed() {
        List<String> order = new ArrayList<>(runners)
                .stream()
                        .sorted(AnnotationAwareOrderComparator.INSTANCE)
                        .map(r -> r.getClass().getSimpleName())
                        .toList();
        assertThat(order).contains("PlatformAdminBootstrap", "DevDataSeeder");
        assertThat(order.indexOf("PlatformAdminBootstrap")).isLessThan(order.indexOf("DevDataSeeder"));
    }
}
