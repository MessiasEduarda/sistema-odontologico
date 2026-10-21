package br.com.odontosaas.platform;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.AppProperties;
import br.com.odontosaas.identity.ClinicProvisioning;
import br.com.odontosaas.tenancy.Tenant;
import br.com.odontosaas.tenancy.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Inicialização de dados:
 * <ul>
 *   <li><b>Bootstrap (qualquer ambiente):</b> cria o primeiro administrador da plataforma a partir de
 *       {@code PLATFORM_ADMIN_EMAIL}/{@code PLATFORM_ADMIN_PASSWORD}, apenas se ainda não existir nenhum.
 *   <li><b>Seed (somente dev):</b> massa 100% FICTÍCIA com duas clínicas, para desenvolvimento e demonstração.
 * </ul>
 */
@Component
class PlatformSeeds implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformSeeds.class);

    private final AppProperties app;
    private final ClinicProvisioning provisioning;
    private final TenantRepository tenants;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final SubscriptionService subscriptions;
    private final DevDemoData demo;
    private final DevFinanceDemo finance;
    private final Environment environment;

    PlatformSeeds(
            AppProperties app,
            ClinicProvisioning provisioning,
            TenantRepository tenants,
            AuditService audit,
            TransactionTemplate tx,
            SubscriptionService subscriptions,
            DevDemoData demo,
            DevFinanceDemo finance,
            Environment environment) {
        this.environment = environment;
        this.app = app;
        this.provisioning = provisioning;
        this.tenants = tenants;
        this.audit = audit;
        this.tx = tx;
        this.subscriptions = subscriptions;
        this.demo = demo;
        this.finance = finance;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapPlatformAdmin();
        if (app.seed() != null && app.seed().enabled()) {
            // O seed cria usuários (inclusive administrador da plataforma) com senha conhecida: jamais fora do dev.
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("app.seed.enabled só é permitido no perfil dev. Perfis ativos: "
                        + String.join(",", environment.getActiveProfiles()));
            }
            seedDevelopmentData(app.seed().password());
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

    private void seedDevelopmentData(String password) {
        tx.executeWithoutResult(status -> {
            if (!provisioning.userExists("admin@odonto.example")) {
                createDevelopmentUsers(password);
            }
            // Assinaturas e dados de demonstração (idempotentes).
            for (String slug : java.util.List.of("sorriso-demo", "exemplo-dois")) {
                tenants.findBySlugIgnoreCase(slug).ifPresent(t -> subscriptions.startTrial(t.getId(), "CLINICA_PLUS"));
            }
            tenants.findBySlugIgnoreCase("sorriso-demo")
                    .ifPresent(t -> demo.fill(
                            t.getId(),
                            provisioning.userId("dentista@sorriso.example").orElse(null),
                            provisioning.userId("dona@sorriso.example").orElse(null)));
            tenants.findBySlugIgnoreCase("sorriso-demo")
                    .ifPresent(t -> finance.fill(
                            t.getId(),
                            provisioning.userId("dona@sorriso.example").orElse(null)));
            tenants.findBySlugIgnoreCase("exemplo-dois")
                    .ifPresent(t -> finance.fill(
                            t.getId(),
                            provisioning.userId("dono@exemplo2.example").orElse(null)));
        });
    }

    private void createDevelopmentUsers(String password) {
        {
            provisioning.createUser("admin@odonto.example", "Admin Plataforma (fictício)", password, true);

            UUID sorriso = clinic("Clínica Sorriso Demo", "sorriso-demo");
            UUID exemplo = clinic("Clínica Exemplo Dois", "exemplo-dois");

            UUID dona = provisioning.createUser("dona@sorriso.example", "Ana Exemplo (fictícia)", password, false);
            provisioning.addMember(sorriso, dona, "OWNER");

            UUID dentista =
                    provisioning.createUser("dentista@sorriso.example", "Bruno Teste (fictício)", password, false);
            provisioning.addMember(sorriso, dentista, "DENTIST");
            provisioning.addMember(exemplo, dentista, "DENTIST");

            UUID recepcao =
                    provisioning.createUser("recepcao@sorriso.example", "Carla Modelo (fictícia)", password, false);
            provisioning.addMember(sorriso, recepcao, "RECEPTION");

            UUID dono2 = provisioning.createUser("dono@exemplo2.example", "Diego Amostra (fictício)", password, false);
            provisioning.addMember(exemplo, dono2, "OWNER");

            log.info("Seed de desenvolvimento criado (dados fictícios). Usuários em docs/guias/ambiente-local.md");
        }
    }

    private UUID clinic(String name, String slug) {
        Tenant tenant = tenants.saveAndFlush(new Tenant(name, slug));
        provisioning.createDefaultRoles(tenant.getId(), null);
        return tenant.getId();
    }
}
