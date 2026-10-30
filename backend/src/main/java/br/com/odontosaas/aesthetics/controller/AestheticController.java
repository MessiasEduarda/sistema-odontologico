package br.com.odontosaas.aesthetics.controller;

import br.com.odontosaas.aesthetics.dto.request.AestheticRequest;
import br.com.odontosaas.aesthetics.dto.response.AestheticView;
import br.com.odontosaas.aesthetics.service.AestheticService;
import br.com.odontosaas.shared.VoidRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

@RestController
@Tag(name = "Prontuário")
public class AestheticController {

    private static final String READ = "hasAuthority('clinical_records:read')";
    private static final String WRITE = "hasAuthority('clinical_records:write')";
    private static final String BASE = "/api/v1/patients/{patientId}";

    private final AestheticService aesthetic;

    public AestheticController(AestheticService aesthetic) {
        this.aesthetic = aesthetic;
    }

    @GetMapping(BASE + "/aesthetic-procedures")
    @PreAuthorize(READ)
    public List<AestheticView> aesthetics(@PathVariable UUID patientId) {
        return aesthetic.byPatient(patientId);
    }

    @PostMapping(BASE + "/aesthetic-procedures")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra aplicação de harmonização (produto, lote, validade, regiões)")
    public AestheticView addAesthetic(@PathVariable UUID patientId, @Valid @RequestBody AestheticRequest request) {
        return aesthetic.create(patientId, request);
    }

    @PostMapping(BASE + "/aesthetic-procedures/{id}/void")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void voidAesthetic(
            @PathVariable UUID patientId, @PathVariable UUID id, @Valid @RequestBody VoidRequest request) {
        aesthetic.voidRecord(patientId, id, request.reason());
    }

    @GetMapping("/api/v1/aesthetic-procedures")
    @PreAuthorize(READ)
    @Operation(summary = "Aplicações da clínica: busca por lote (rastreabilidade), paciente/produto e retornos")
    public List<AestheticView> searchAesthetics(
            @RequestParam(required = false) String batch,
            @Parameter(description = "Texto contido no nome do paciente ou do produto") @RequestParam(required = false)
                    String searchTerm,
            @RequestParam(required = false) LocalDate returnsUntil) {
        return aesthetic.search(batch, searchTerm, returnsUntil);
    }
}
