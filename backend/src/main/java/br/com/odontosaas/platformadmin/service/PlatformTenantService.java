package br.com.odontosaas.platformadmin.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.ClinicProvisioning;
import br.com.odontosaas.platformadmin.dto.request.CreateTenantRequest;
import br.com.odontosaas.platformadmin.dto.response.TenantView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.PageResponse;
import br.com.odontosaas.subscription.service.SubscriptionService;
import br.com.odontosaas.tenancy.TenantContext;
import br.com.odontosaas.tenancy.entity.Tenant;
import br.com.odontosaas.tenancy.repository.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de clínicas pela administração da plataforma. Não acessa dados internos das clínicas. */
@Service
public class PlatformTenantService {

    private final TenantRepository tenants;
    private final ClinicProvisioning provisioning;
    private final AuditService audit;
    private final SubscriptionService subscriptions;

    public PlatformTenantService(
            TenantRepository tenants,
            ClinicProvisioning provisioning,
            AuditService audit,
            SubscriptionService subscriptions) {
        this.tenants = tenants;
        this.provisioning = provisioning;
        this.audit = audit;
        this.subscriptions = subscriptions;
    }

    /** Cria a clínica, seus papéis padrão e convida o responsável (que define a própria senha pelo e-mail). */
    @Transactional
    public TenantView create(CreateTenantRequest request, UUID actorUserId) {
        if (tenants.existsBySlugIgnoreCase(request.slug())) {
            throw ApiException.conflict("SLUG_TAKEN", "Já existe uma clínica com esse identificador.");
        }
        Tenant tenant = tenants.saveAndFlush(new Tenant(request.name().strip(), request.slug()));
        provisioning.createDefaultRoles(tenant.getId(), actorUserId);
        subscriptions.startTrial(tenant.getId(), request.planCode());
        provisioning.inviteOwner(tenant.getId(), request.ownerEmail(), actorUserId);
        TenantContext.runAs(tenant.getId(), actorUserId, () -> {
            audit.record(AuditAction.TENANT_CREATED, "tenant", tenant.getId(), Map.of("slug", tenant.getSlug()));
            return null;
        });
        return toView(tenant);
    }

    /**
     * Reenvia o convite de responsável (o anterior, se pendente, deixa de valer). Útil quando o e-mail não chegou ou o
     * endereço estava errado. 404 se a clínica não existir; 409 ALREADY_MEMBER se a pessoa já entrou.
     */
    @Transactional
    public void resendOwnerInvite(UUID tenantId, String ownerEmail, UUID actorUserId) {
        if (!tenants.existsById(tenantId)) {
            throw ApiException.notFound("Clínica");
        }
        provisioning.inviteOwner(tenantId, ownerEmail, actorUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantView> list(int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("name"));
        return PageResponse.of(tenants.findAll(request), PlatformTenantService::toView);
    }

    private static TenantView toView(Tenant t) {
        return new TenantView(t.getId(), t.getName(), t.getSlug(), t.getStatus().name(), t.getCreatedAt());
    }
}
