package br.com.odontosaas.platformadmin.controller;

import br.com.odontosaas.platformadmin.dto.request.StatusRequest;
import br.com.odontosaas.platformadmin.dto.response.Metrics;
import br.com.odontosaas.platformadmin.dto.response.PlatformEvent;
import br.com.odontosaas.platformadmin.dto.response.TenantRow;
import br.com.odontosaas.platformadmin.service.PlatformAdminService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administração da plataforma: indicadores, clínicas (situação) e auditoria. Planos e assinaturas: módulo subscription. */
@RestController
@Tag(name = "Plataforma: administração", description = "Planos, assinaturas, indicadores e auditoria")
public class PlatformAdminController {

    private static final String ADMIN = "hasRole('PLATFORM_ADMIN')";

    private final PlatformAdminService admin;

    public PlatformAdminController(PlatformAdminService admin) {
        this.admin = admin;
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

    @PostMapping("/api/v1/platform/clinics/{id}/status")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Suspende, reativa ou cancela a clínica (suspensa = equipe sem acesso)")
    public void setStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        admin.setTenantStatus(id, request.status());
    }

    @GetMapping("/api/v1/platform/audit")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Histórico de ações administrativas e de autenticação")
    public List<PlatformEvent> audit(@RequestParam(required = false) String action) {
        return admin.events(action);
    }
}
