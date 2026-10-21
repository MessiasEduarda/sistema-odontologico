package br.com.odontosaas.treatment.controller;

import br.com.odontosaas.treatment.dto.request.ApproveRequest;
import br.com.odontosaas.treatment.dto.request.PlanRequest;
import br.com.odontosaas.treatment.dto.request.ReasonRequest;
import br.com.odontosaas.treatment.dto.response.PlanSummary;
import br.com.odontosaas.treatment.dto.response.PlanView;
import br.com.odontosaas.treatment.service.TreatmentService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/treatment-plans")
@Tag(name = "Planos de tratamento e orçamentos")
public class TreatmentController {

    private static final String READ = "hasAuthority('treatments:read')";
    private static final String WRITE = "hasAuthority('treatments:write')";

    private final TreatmentService service;

    public TreatmentController(TreatmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Lista orçamentos", description = "Filtros: patientId, status")
    public List<PlanSummary> list(
            @RequestParam(required = false) UUID patientId, @RequestParam(required = false) String status) {
        return service.list(patientId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public PlanView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria orçamento (rascunho). Totais calculados pelo servidor.")
    public PlanView create(@Valid @RequestBody PlanRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Edita orçamento em rascunho/apresentado", description = "422 PLAN_LOCKED")
    public PlanView update(@PathVariable UUID id, @Valid @RequestBody PlanRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/propose")
    @PreAuthorize(WRITE)
    @Operation(summary = "Marca como apresentado ao paciente")
    public PlanView propose(@PathVariable UUID id) {
        return service.propose(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(WRITE)
    @Operation(summary = "Registra a aprovação", description = "Opcional: gera conta a receber parcelada.")
    public PlanView approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
        return service.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(WRITE)
    public PlanView reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return service.reject(id, request.reason());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE)
    public PlanView cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return service.cancel(id, request.reason());
    }
}
