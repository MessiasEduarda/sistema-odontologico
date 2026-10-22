package br.com.odontosaas.subscription.controller;

import br.com.odontosaas.shared.Access;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.subscription.dto.request.PlanRequest;
import br.com.odontosaas.subscription.dto.request.SubscriptionRequest;
import br.com.odontosaas.subscription.dto.response.PlanView;
import br.com.odontosaas.subscription.dto.response.SubscriptionView;
import br.com.odontosaas.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Plataforma: administração", description = "Planos, assinaturas, indicadores e auditoria")
public class SubscriptionController {

    private static final String ADMIN = "hasRole('PLATFORM_ADMIN')";

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
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

    @GetMapping("/api/v1/clinic/subscription")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Plano e limites da clínica ativa (para a equipe)")
    public SubscriptionView clinicSubscription() {
        return subscriptions.currentClinic().orElseThrow(() -> ApiException.notFound("Assinatura"));
    }
}
