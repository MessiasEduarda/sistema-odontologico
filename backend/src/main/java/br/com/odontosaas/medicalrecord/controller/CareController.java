package br.com.odontosaas.medicalrecord.controller;

import br.com.odontosaas.medicalrecord.dto.request.CareIssueRequest;
import br.com.odontosaas.medicalrecord.dto.request.CareTemplateRequest;
import br.com.odontosaas.medicalrecord.dto.response.CareTemplateView;
import br.com.odontosaas.medicalrecord.dto.response.DocumentView;
import br.com.odontosaas.medicalrecord.service.CareService;
import br.com.odontosaas.shared.Access;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Orientações ao paciente: modelos de orientação (pré e pós-procedimento) e entrega registrada no prontuário. */
@RestController
@Tag(name = "Prontuário")
public class CareController {

    private static final String WRITE = "hasAuthority('clinical_records:write')";
    private static final String CARE_MANAGE = "hasAnyAuthority('clinic:settings:manage', 'clinical_records:write')";
    private static final String BASE = "/api/v1/patients/{patientId}";

    private final CareService care;

    public CareController(CareService care) {
        this.care = care;
    }

    @GetMapping("/api/v1/clinic/care-templates")
    @PreAuthorize(Access.MEMBER)
    public List<CareTemplateView> careTemplates(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return care.templates(includeInactive);
    }

    @PostMapping("/api/v1/clinic/care-templates")
    @PreAuthorize(CARE_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria modelo de orientação (pré ou pós-procedimento)")
    public CareTemplateView createCareTemplate(@Valid @RequestBody CareTemplateRequest request) {
        return care.create(request);
    }

    @PutMapping("/api/v1/clinic/care-templates/{id}")
    @PreAuthorize(CARE_MANAGE)
    public CareTemplateView updateCareTemplate(@PathVariable UUID id, @Valid @RequestBody CareTemplateRequest request) {
        return care.update(id, request);
    }

    @PostMapping(BASE + "/care-instructions")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Entrega orientação ao paciente (registra no prontuário e, se pedido, envia por e-mail)")
    public DocumentView issueCare(@PathVariable UUID patientId, @Valid @RequestBody CareIssueRequest request) {
        return care.issue(patientId, request);
    }
}
