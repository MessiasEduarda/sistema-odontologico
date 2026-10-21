package br.com.odontosaas.devdata.service;

import br.com.odontosaas.identity.ClinicProvisioning;
import br.com.odontosaas.identity.config.AppProperties;
import br.com.odontosaas.subscription.service.SubscriptionService;
import br.com.odontosaas.tenancy.entity.Tenant;
import br.com.odontosaas.tenancy.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Massa de dados 100% FICTÍCIA para desenvolvimento e demonstração (duas clínicas, usuários, agenda, financeiro).
 * Existe SOMENTE no perfil {@code dev} e só roda com {@code app.seed.enabled=true}. A recusa de subir com o seed
 * ligado fora do dev fica em {@code PlatformAdminBootstrap}, que roda antes ({@code @Order(10)}).
 */
@Component
@Profile("dev")
@Order(20)
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final AppProperties app;
    private final ClinicProvisioning provisioning;
    private final TenantRepository tenants;
    private final TransactionTemplate tx;
    private final SubscriptionService subscriptions;
    private final DevDemoData demo;
    private final DevFinanceDemo finance;

    DevDataSeeder(
            AppProperties app,
            ClinicProvisioning provisioning,
            TenantRepository tenants,
            TransactionTemplate tx,
            SubscriptionService subscriptions,
            DevDemoData demo,
            DevFinanceDemo finance) {
        this.app = app;
        this.provisioning = provisioning;
        this.tenants = tenants;
        this.tx = tx;
        this.subscriptions = subscriptions;
        this.demo = demo;
        this.finance = finance;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (app.seed() != null && app.seed().enabled()) {
            seedDevelopmentData(app.seed().password());
        }
    }

    private void seedDevelopmentData(String password) {
        tx.executeWithoutResult(status -> {
            if (!provisioning.userExists("admin@odonto.example")) {
                createDevelopmentUsers(password);
            }
            // Assinaturas e dados de demonstração (idempotentes).
            for (String slug : List.of("sorriso-demo", "exemplo-dois")) {
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
        provisioning.createUser("admin@odonto.example", "Admin Plataforma (fictício)", password, true);

        UUID sorriso = clinic("Clínica Sorriso Demo", "sorriso-demo");
        UUID exemplo = clinic("Clínica Exemplo Dois", "exemplo-dois");

        UUID dona = provisioning.createUser("dona@sorriso.example", "Ana Exemplo (fictícia)", password, false);
        provisioning.addMember(sorriso, dona, "OWNER");

        UUID dentista = provisioning.createUser("dentista@sorriso.example", "Bruno Teste (fictício)", password, false);
        provisioning.addMember(sorriso, dentista, "DENTIST");
        provisioning.addMember(exemplo, dentista, "DENTIST");

        UUID recepcao = provisioning.createUser("recepcao@sorriso.example", "Carla Modelo (fictícia)", password, false);
        provisioning.addMember(sorriso, recepcao, "RECEPTION");

        UUID dono2 = provisioning.createUser("dono@exemplo2.example", "Diego Amostra (fictício)", password, false);
        provisioning.addMember(exemplo, dono2, "OWNER");

        log.info("Seed de desenvolvimento criado (dados fictícios).");
    }

    private UUID clinic(String name, String slug) {
        Tenant tenant = tenants.saveAndFlush(new Tenant(name, slug));
        provisioning.createDefaultRoles(tenant.getId(), null);
        return tenant.getId();
    }
}
