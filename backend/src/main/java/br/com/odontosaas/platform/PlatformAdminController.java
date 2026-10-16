package br.com.odontosaas.platform;

import br.com.odontosaas.platform.PlatformAdminService.Metrics;
import br.com.odontosaas.platform.PlatformAdminService.PlatformEvent;
import br.com.odontosaas.platform.PlatformAdminService.TenantRow;
import br.com.odontosaas.platform.SubscriptionService.PlanRequest;
import br.com.odontosaas.platform.SubscriptionService.PlanView;
import br.com.odontosaas.platform.SubscriptionService.SubscriptionRequest;
import br.com.odontosaas.platform.SubscriptionService.SubscriptionView;
import br.com.odontosaas.shared.Access;
import br.com.odontosaas.shared.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Plataforma: administração", description = "Planos, assinaturas, indicadores e auditoria")
public class PlatformAdminController {

    private static final String ADMIN = "hasRole('PLATFORM_ADMIN')";

    private final PlatformAdminService admin;
    private final SubscriptionService subscriptions;

    public PlatformAdminController(PlatformAdminService admin, SubscriptionService subscriptions) {
        this.admin = admin;
        this.subscriptions = subscriptions;
    }

    @GetMapping("/api/v1/platform/metrics")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Indicadores gerais do SaaS")
    public Metrics metrics() {
        return admin.metrics();
    }

    @GetMapping("/api/v1/platform/clinics")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Clínicas com plano e situação da assinatura")
    public List<TenantRow> clinics(@RequestParam(required = false) String q) {
        return admin.tenants(q);
    }

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|CANCELLED")
            String status) {}

    @PostMapping("/api/v1/platform/clinics/{id}/status")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Suspende, reativa ou cancela a clínica (suspensa = equipe sem acesso)")
    public void setStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        admin.setTenantStatus(id, request.status());
    }

    @GetMapping("/api/v1/platform/clinics/{id}/subscription")
    @PreAuthorize(ADMIN)
    public SubscriptionView subscription(@PathVariable UUID id) {
        return subscriptions.subscription(id).orElseThrow(() -> ApiException.notFound("Assinatura"));
    }

    @PutMapping("/api/v1/platform/clinics/{id}/subscription")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Altera plano/situação da assinatura (inadimplência, cancelamento...)")
    public SubscriptionView updateSubscription(@PathVariable UUID id, @Valid @RequestBody SubscriptionRequest request) {
        return subscriptions.update(id, request);
    }

    @GetMapping("/api/v1/platform/plans")
    @PreAuthorize(ADMIN)
    public List<PlanView> plans() {
        return subscriptions.plans();
    }

    @PostMapping("/api/v1/platform/plans")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public PlanView createPlan(@Valid @RequestBody PlanRequest request) {
        return subscriptions.savePlan(null, request);
    }

    @PutMapping("/api/v1/platform/plans/{id}")
    @PreAuthorize(ADMIN)
    public PlanView updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest request) {
        return subscriptions.savePlan(id, request);
    }

    @GetMapping("/api/v1/platform/audit")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Histórico de ações administrativas e de autenticação")
    public List<PlatformEvent> audit(@RequestParam(required = false) String action) {
        return admin.events(action);
    }

    @GetMapping("/api/v1/clinic/subscription")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Plano e limites da clínica ativa (para a equipe)")
    public SubscriptionView clinicSubscription() {
        return subscriptions.currentClinic().orElseThrow(() -> ApiException.notFound("Assinatura"));
    }
}
