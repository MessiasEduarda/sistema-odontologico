package br.com.odontosaas.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.ClinicProvisioning;
import br.com.odontosaas.identity.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

/** A recusa de subir com o seed de desenvolvimento ligado fora do perfil dev (item 7 do relatório de QA). */
class PlatformAdminBootstrapTest {

    private static PlatformAdminBootstrap bootstrap(boolean seedEnabled, String... profiles) {
        AppProperties app = mock(AppProperties.class);
        when(app.seed()).thenReturn(new AppProperties.Seed(seedEnabled, "senha-qualquer"));
        when(app.bootstrap()).thenReturn(null);
        MockEnvironment environment = new MockEnvironment();
        if (profiles.length > 0) environment.setActiveProfiles(profiles);
        return new PlatformAdminBootstrap(
                app,
                mock(ClinicProvisioning.class),
                mock(AuditService.class),
                mock(TransactionTemplate.class),
                environment);
    }

    @Test
    void refusesToStartWithSeedEnabledOutsideDevProfile() {
        assertThatThrownBy(() -> bootstrap(true, "prod").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.seed.enabled");
        assertThatThrownBy(() -> bootstrap(true, "test").run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsSeedInDevProfileAndNoSeedAnywhere() {
        assertThatCode(() -> bootstrap(true, "dev").run(null)).doesNotThrowAnyException();
        assertThatCode(() -> bootstrap(false, "prod").run(null)).doesNotThrowAnyException();
    }
}
