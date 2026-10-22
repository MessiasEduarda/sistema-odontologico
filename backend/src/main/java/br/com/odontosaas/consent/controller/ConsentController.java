package br.com.odontosaas.consent.controller;

import br.com.odontosaas.consent.dto.request.ConsentRequest;
import br.com.odontosaas.consent.dto.request.ConsentTemplateRequest;
import br.com.odontosaas.consent.dto.request.ExamplesRequest;
import br.com.odontosaas.consent.dto.request.LinkRequest;
import br.com.odontosaas.consent.dto.request.SignRequest;
import br.com.odontosaas.consent.dto.response.ConsentLinkView;
import br.com.odontosaas.consent.dto.response.ConsentListItem;
import br.com.odontosaas.consent.dto.response.ConsentTemplateView;
import br.com.odontosaas.consent.dto.response.ConsentView;
import br.com.odontosaas.consent.dto.response.ExamplesResult;
import br.com.odontosaas.consent.service.ConsentService;
import br.com.odontosaas.shared.Access;
import br.com.odontosaas.shared.VoidRequest;
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

@RestController
@Tag(name = "Prontuário")
public class ConsentController {

    private static final String CONSENT_WRITE = "hasAnyAuthority('clinical_records:write', 'patients:write')";
    private static final String CONSENT_READ = "hasAnyAuthority('clinical_records:read', 'patients:write')";
    private static final String MANAGE = "hasAuthority('clinic:settings:manage')";
    private static final String BASE = "/api/v1/patients/{patientId}";

    private final ConsentService consents;

    public ConsentController(ConsentService consents) {
        this.consents = consents;
    }

    @GetMapping("/api/v1/clinic/consent-templates")
    @PreAuthorize(Access.MEMBER)
    public List<ConsentTemplateView> consentTemplates(@RequestParam(defaultValue = "false") boolean allVersions) {
        return consents.templates(allVersions);
    }

    @PostMapping("/api/v1/clinic/consent-templates")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cria modelo de termo",
            description = "Campos: {{paciente}} {{cpf}} {{procedimento}} {{profissional}} {{cro}} {{clinica}} {{data}}."
                    + " Categorias: DENTAL, AESTHETIC, IMAGE, PRIVACY, GENERAL")
    public ConsentTemplateView createTemplate(@Valid @RequestBody ConsentTemplateRequest request) {
        return consents.createTemplate(request);
    }

    @PutMapping("/api/v1/clinic/consent-templates/{id}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Edita modelo (gera nova versão)")
    public ConsentTemplateView newVersion(@PathVariable UUID id, @Valid @RequestBody ConsentTemplateRequest request) {
        return consents.newVersion(id, request);
    }

    @PostMapping("/api/v1/clinic/consent-templates/{id}/archive")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveTemplate(@PathVariable UUID id) {
        consents.archiveTemplate(id);
    }

    @PostMapping("/api/v1/clinic/consent-templates/examples")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Adiciona modelos de exemplo (odontologia, harmonização, uso de imagem, LGPD) que faltarem")
    public ExamplesResult addExamples(@RequestBody(required = false) ExamplesRequest request) {
        return new ExamplesResult(consents.addExamples(request == null ? null : request.categories()));
    }

    @GetMapping("/api/v1/consents")
    @PreAuthorize(CONSENT_READ)
    @Operation(summary = "Termos de todos os pacientes (filtros: situação, categoria, paciente)")
    public List<ConsentListItem> allConsents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return consents.listAll(status, category, q);
    }

    @GetMapping(BASE + "/consents")
    @PreAuthorize(CONSENT_READ)
    public List<ConsentView> consents(@PathVariable UUID patientId) {
        return consents.list(patientId);
    }

    @GetMapping(BASE + "/consents/{consentId}")
    @PreAuthorize(CONSENT_READ)
    public ConsentView consent(@PathVariable UUID patientId, @PathVariable UUID consentId) {
        return consents.get(patientId, consentId);
    }

    @PostMapping(BASE + "/consents")
    @PreAuthorize(CONSENT_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emite termo para o paciente a partir de um modelo")
    public ConsentView issueConsent(@PathVariable UUID patientId, @Valid @RequestBody ConsentRequest request) {
        return consents.issue(patientId, request);
    }

    @PostMapping(BASE + "/consents/{consentId}/sign")
    @PreAuthorize(CONSENT_WRITE)
    @Operation(summary = "Assinatura presencial (na tela da clínica)")
    public ConsentView signInClinic(
            @PathVariable UUID patientId, @PathVariable UUID consentId, @Valid @RequestBody SignRequest request) {
        return consents.signInClinic(
                patientId, consentId, request.signerName(), request.signerDocument(), request.signatureImage());
    }

    @PostMapping(BASE + "/consents/{consentId}/link")
    @PreAuthorize(CONSENT_WRITE)
    @Operation(summary = "Gera link de assinatura (7 dias) e opcionalmente envia por e-mail")
    public ConsentLinkView consentLink(
            @PathVariable UUID patientId, @PathVariable UUID consentId, @RequestBody(required = false) LinkRequest r) {
        return consents.createLink(patientId, consentId, r != null && r.sendEmail());
    }

    @PostMapping(BASE + "/consents/{consentId}/revoke")
    @PreAuthorize(CONSENT_WRITE)
    public ConsentView revokeConsent(
            @PathVariable UUID patientId, @PathVariable UUID consentId, @Valid @RequestBody VoidRequest request) {
        return consents.revoke(patientId, consentId, request.reason());
    }
}
