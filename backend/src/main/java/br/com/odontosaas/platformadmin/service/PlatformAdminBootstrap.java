package br.com.odontosaas.platformadmin.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.ClinicProvisioning;
import br.com.odontosaas.identity.config.AppProperties;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Inicialização que roda em QUALQUER ambiente: cria o primeiro administrador da plataforma a partir de
 * {@code PLATFORM_ADMIN_EMAIL}/{@code PLATFORM_ADMIN_PASSWORD}, apenas se ainda não existir nenhum.
 *
 * <p>Também recusa subir se o seed de desenvolvimento ({@code app.seed.enabled}) estiver ligado fora do perfil
 * {@code dev}: ele cria usuários, inclusive administrador da plataforma, com senha conhecida. Por isso este runner não
 * tem {@code @Profile}. Roda antes ({@code @Order(10)}) do seed de dev ({@code devdata}, {@code @Order(20)}).
 */
@Component
@Order(10)
class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final AppProperties app;
    private final ClinicProvisioning provisioning;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Environment environment;

    PlatformAdminBootstrap(
            AppProperties app,
            ClinicProvisioning provisioning,
            AuditService audit,
            TransactionTemplate tx,
            Environment environment) {
        this.app = app;
        this.provisioning = provisioning;
        this.audit = audit;
        this.tx = tx;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapPlatformAdmin();
        if (app.seed() != null && app.seed().enabled() && !environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException("app.seed.enabled só é permitido no perfil dev. Perfis ativos: "
                    + String.join(",", environment.getActiveProfiles()));
        }
    }

    private void bootstrapPlatformAdmin() {
        AppProperties.Bootstrap b = app.bootstrap();
        if (b == null
                || !StringUtils.hasText(b.platformAdminEmail())
                || !StringUtils.hasText(b.platformAdminPassword())) return;
        tx.executeWithoutResult(status -> {
            if (provisioning.platformAdminExists() || provisioning.userExists(b.platformAdminEmail())) return;
            UUID id = provisioning.createUser(
                    b.platformAdminEmail(), "Administrador da plataforma", b.platformAdminPassword(), true);
            audit.recordGlobal(AuditAction.PLATFORM_ADMIN_BOOTSTRAPPED, id, "user", id, Map.of());
            log.info("Administrador da plataforma inicial criado.");
        });
    }
}
