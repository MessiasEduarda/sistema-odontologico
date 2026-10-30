package br.com.odontosaas.patients.controller;

import br.com.odontosaas.patients.dto.request.ImportRequest;
import br.com.odontosaas.patients.dto.request.PatientRequest;
import br.com.odontosaas.patients.dto.response.ImportResult;
import br.com.odontosaas.patients.dto.response.PatientSummary;
import br.com.odontosaas.patients.dto.response.PatientView;
import br.com.odontosaas.patients.service.PatientExportService;
import br.com.odontosaas.patients.service.PatientService;
import br.com.odontosaas.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/patients")
@Tag(name = "Pacientes")
public class PatientController {

    private final PatientService service;
    private final PatientExportService exports;

    public PatientController(PatientService service, PatientExportService exports) {
        this.service = service;
        this.exports = exports;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('patients:read')")
    @Operation(
            summary = "Busca pacientes",
            description = "searchTerm: nome, CPF, telefone ou e-mail; active; page/size. Permissão: patients:read")
    public PageResponse<PatientSummary> search(
            @Parameter(
                            description =
                                    "Nome, nome social ou e-mail (contém); CPF (começa com); telefone (contém). CPF e telefone exigem ao menos 3 dígitos")
                    @RequestParam(required = false)
                    String searchTerm,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(searchTerm, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('patients:read')")
    public PatientView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('patients:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra paciente", description = "409 DUPLICATE_CPF. Menor de 18 exige responsável.")
    public PatientView create(@Valid @RequestBody PatientRequest request) {
        return service.create(request);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('patients:write')")
    @Operation(
            summary = "Importa pacientes de planilha",
            description = "Linhas já convertidas da planilha (CSV). dryRun=true só valida. Até 2000 por vez; "
                    + "inválidas e CPFs repetidos voltam em issues e não entram.")
    public ImportResult importPatients(@Valid @RequestBody ImportRequest request) {
        return service.importRows(request.rows(), request.dryRun());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('patients:write')")
    @Operation(summary = "Atualiza paciente", description = "Envie version (409 STALE_VERSION se desatualizado).")
    public PatientView update(@PathVariable UUID id, @Valid @RequestBody PatientRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('patients:read') and hasAuthority('clinical_records:read')")
    @Operation(
            summary = "Exporta todos os dados do paciente (LGPD)",
            description = "JSON com cadastro, consultas, prontuário, termos, orçamentos e financeiro. Registrado na "
                    + "auditoria. Permissões: patients:read e clinical_records:read")
    public java.util.Map<String, Object> export(@PathVariable UUID id) {
        return exports.export(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('patients:write')")
    public PatientView deactivate(@PathVariable UUID id) {
        return service.setActive(id, false);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('patients:write')")
    public PatientView activate(@PathVariable UUID id) {
        return service.setActive(id, true);
    }
}
