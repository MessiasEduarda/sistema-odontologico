package br.com.odontosaas.clinical;

import br.com.odontosaas.clinical.AestheticService.AestheticRequest;
import br.com.odontosaas.clinical.AestheticService.AestheticView;
import br.com.odontosaas.clinical.ClinicalDtos.AnamnesisRequest;
import br.com.odontosaas.clinical.ClinicalDtos.AnamnesisView;
import br.com.odontosaas.clinical.ClinicalDtos.AttachmentView;
import br.com.odontosaas.clinical.ClinicalDtos.CareIssueRequest;
import br.com.odontosaas.clinical.ClinicalDtos.CareTemplateRequest;
import br.com.odontosaas.clinical.ClinicalDtos.CareTemplateView;
import br.com.odontosaas.clinical.ClinicalDtos.ChartEntryRequest;
import br.com.odontosaas.clinical.ClinicalDtos.ChartEntryView;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentLinkView;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentListItem;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentRequest;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentTemplateRequest;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentTemplateView;
import br.com.odontosaas.clinical.ClinicalDtos.ConsentView;
import br.com.odontosaas.clinical.ClinicalDtos.DocumentRequest;
import br.com.odontosaas.clinical.ClinicalDtos.DocumentView;
import br.com.odontosaas.clinical.ClinicalDtos.FormRequest;
import br.com.odontosaas.clinical.ClinicalDtos.FormView;
import br.com.odontosaas.clinical.ClinicalDtos.IssuedDocument;
import br.com.odontosaas.clinical.ClinicalDtos.NoteRequest;
import br.com.odontosaas.clinical.ClinicalDtos.NoteView;
import br.com.odontosaas.clinical.ClinicalDtos.PerformedRequest;
import br.com.odontosaas.clinical.ClinicalDtos.PerformedView;
import br.com.odontosaas.clinical.ClinicalDtos.PublicConsentView;
import br.com.odontosaas.clinical.ClinicalDtos.PublicSignRequest;
import br.com.odontosaas.clinical.ClinicalDtos.RecentAnamnesis;
import br.com.odontosaas.clinical.ClinicalDtos.RecordSummary;
import br.com.odontosaas.clinical.ClinicalDtos.SignRequest;
import br.com.odontosaas.clinical.ClinicalDtos.VoidRequest;
import br.com.odontosaas.shared.Access;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Prontuário odontológico. Leitura: clinical_records:read (toda abertura do prontuário é auditada). Registro:
 * clinical_records:write. Modelos: clinic:settings:manage. Termos: emitir/assinar na clínica exige
 * clinical_records:write ou patients:write.
 */
@RestController
@Tag(name = "Prontuário")
public class ClinicalController {

    private static final String READ = "hasAuthority('clinical_records:read')";
    private static final String WRITE = "hasAuthority('clinical_records:write')";
    private static final String CONSENT_WRITE = "hasAnyAuthority('clinical_records:write', 'patients:write')";
    private static final String CONSENT_READ = "hasAnyAuthority('clinical_records:read', 'patients:write')";
    private static final String MANAGE = "hasAuthority('clinic:settings:manage')";
    private static final String CARE_MANAGE = "hasAnyAuthority('clinic:settings:manage', 'clinical_records:write')";
    private static final String BASE = "/api/v1/patients/{patientId}";

    private final ClinicalService clinical;
    private final ConsentService consents;
    private final AttachmentService attachments;
    private final CareService care;
    private final AestheticService aesthetic;

    public ClinicalController(
            ClinicalService clinical,
            ConsentService consents,
            AttachmentService attachments,
            CareService care,
            AestheticService aesthetic) {
        this.clinical = clinical;
        this.consents = consents;
        this.attachments = attachments;
        this.care = care;
        this.aesthetic = aesthetic;
    }

    @GetMapping(BASE + "/record")
    @PreAuthorize(READ)
    @Operation(summary = "Resumo do prontuário (alertas da anamnese, contadores). Auditado.")
    public RecordSummary summary(@PathVariable UUID patientId) {
        return clinical.summary(patientId);
    }

    // ------------------------------------------------------------------ Anamnese

    @GetMapping("/api/v1/clinic/anamnesis-forms")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Fichas de anamnese da clínica (cria as fichas padrão na primeira consulta)")
    public List<FormView> forms(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return clinical.forms(includeInactive);
    }

    @PostMapping("/api/v1/clinic/anamnesis-forms")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria ficha de anamnese (odontológica, harmonização ou outra)")
    public FormView createForm(@Valid @RequestBody FormRequest request) {
        return clinical.createForm(request);
    }

    @PutMapping("/api/v1/clinic/anamnesis-forms/{id}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Edita ficha (anamneses já preenchidas mantêm as perguntas da época)")
    public FormView updateForm(@PathVariable UUID id, @Valid @RequestBody FormRequest request) {
        return clinical.updateForm(id, request);
    }

    public record ActiveRequest(boolean active) {}

    @PostMapping("/api/v1/clinic/anamnesis-forms/{id}/active")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Ativa ou desativa a ficha")
    public FormView setFormActive(@PathVariable UUID id, @RequestBody ActiveRequest request) {
        return clinical.setFormActive(id, request.active());
    }

    @GetMapping("/api/v1/anamneses")
    @PreAuthorize(READ)
    @Operation(summary = "Anamneses recentes da clínica (filtros: tipo de ficha, somente com alertas, paciente)")
    public List<RecentAnamnesis> recentAnamneses(
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "false") boolean alertsOnly,
            @RequestParam(required = false) String q) {
        return clinical.recentAnamneses(kind, alertsOnly, q);
    }

    @GetMapping(BASE + "/anamneses")
    @PreAuthorize(READ)
    @Operation(summary = "Histórico de anamneses (mais recente primeiro)")
    public List<AnamnesisView> anamneses(@PathVariable UUID patientId) {
        return clinical.anamneses(patientId);
    }

    @PostMapping(BASE + "/anamneses")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra nova anamnese (nova versão; as anteriores ficam no histórico)")
    public AnamnesisView fillAnamnesis(@PathVariable UUID patientId, @Valid @RequestBody AnamnesisRequest request) {
        return clinical.fillAnamnesis(patientId, request);
    }

    // ------------------------------------------------------------------ Odontograma

    @GetMapping(BASE + "/dental-chart")
    @PreAuthorize(READ)
    public List<ChartEntryView> chart(@PathVariable UUID patientId) {
        return clinical.chart(patientId);
    }

    @PostMapping(BASE + "/dental-chart")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Marca condição em dente/faces (FDI)")
    public ChartEntryView addChartEntry(@PathVariable UUID patientId, @Valid @RequestBody ChartEntryRequest request) {
        return clinical.addChartEntry(patientId, request);
    }

    @PostMapping(BASE + "/dental-chart/{entryId}/void")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anula marcação com motivo (não apaga)")
    public void voidChartEntry(
            @PathVariable UUID patientId, @PathVariable UUID entryId, @Valid @RequestBody VoidRequest request) {
        clinical.voidChartEntry(patientId, entryId, request.reason());
    }

    // ------------------------------------------------------------------ Evoluções e procedimentos

    @GetMapping(BASE + "/notes")
    @PreAuthorize(READ)
    public List<NoteView> notes(@PathVariable UUID patientId) {
        return clinical.notes(patientId);
    }

    @PostMapping(BASE + "/notes")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra evolução", description = "Não há edição: use amendsNoteId para retificar.")
    public NoteView addNote(@PathVariable UUID patientId, @Valid @RequestBody NoteRequest request) {
        return clinical.addNote(patientId, request);
    }

    @GetMapping(BASE + "/performed-procedures")
    @PreAuthorize(READ)
    public List<PerformedView> performed(@PathVariable UUID patientId) {
        return clinical.performed(patientId);
    }

    @PostMapping(BASE + "/performed-procedures")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra procedimento realizado (opcionalmente baixa item do plano)")
    public PerformedView addPerformed(@PathVariable UUID patientId, @Valid @RequestBody PerformedRequest request) {
        return clinical.addPerformed(patientId, request);
    }

    @PostMapping(BASE + "/performed-procedures/{performedId}/void")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void voidPerformed(
            @PathVariable UUID patientId, @PathVariable UUID performedId, @Valid @RequestBody VoidRequest request) {
        clinical.voidPerformed(patientId, performedId, request.reason());
    }

    // ------------------------------------------------------------------ Documentos

    @GetMapping(BASE + "/documents")
    @PreAuthorize(READ)
    public List<DocumentView> documents(@PathVariable UUID patientId) {
        return clinical.documents(patientId);
    }

    @PostMapping(BASE + "/documents")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emite receituário, atestado, encaminhamento ou outro documento")
    public DocumentView addDocument(@PathVariable UUID patientId, @Valid @RequestBody DocumentRequest request) {
        return clinical.addDocument(patientId, request);
    }

    @GetMapping("/api/v1/clinical-documents")
    @PreAuthorize(READ)
    @Operation(summary = "Documentos emitidos na clínica (filtro por tipo)")
    public List<IssuedDocument> recentDocuments(@RequestParam(required = false) String type) {
        return clinical.recentDocuments(type);
    }

    // ------------------------------------------------------------------ Orientações e cuidados

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

    // ------------------------------------------------------------------ Harmonização orofacial

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
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LocalDate returnsUntil) {
        return aesthetic.search(batch, q, returnsUntil);
    }

    // ------------------------------------------------------------------ Anexos

    @GetMapping(BASE + "/attachments")
    @PreAuthorize(READ)
    public List<AttachmentView> attachments(@PathVariable UUID patientId) {
        return attachments.list(patientId);
    }

    @PostMapping(value = BASE + "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia anexo (PDF, JPG, PNG, WEBP; até 15 MB)")
    public AttachmentView upload(
            @PathVariable UUID patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "description", required = false) String description) {
        return attachments.upload(patientId, file, category, description);
    }

    @GetMapping(BASE + "/attachments/{attachmentId}/download")
    @PreAuthorize(READ)
    @Operation(summary = "Baixa o anexo (auditado)")
    public ResponseEntity<Resource> download(@PathVariable UUID patientId, @PathVariable UUID attachmentId) {
        AttachmentService.StoredFile file = attachments.download(patientId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.filename(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(file.path()));
    }

    @DeleteMapping(BASE + "/attachments/{attachmentId}")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable UUID patientId, @PathVariable UUID attachmentId) {
        attachments.delete(patientId, attachmentId);
    }

    // ------------------------------------------------------------------ Termos de consentimento

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

    public record ExamplesRequest(List<String> categories) {}

    public record ExamplesResult(int created) {}

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

    public record LinkRequest(boolean sendEmail) {}

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

    public record PublicTokenRequest(
            @NotBlank @Size(max = 64) String token) {}

    @PostMapping("/api/v1/public/consents/lookup")
    @Operation(summary = "Público: termo a assinar (link do paciente)", tags = "Público")
    public PublicConsentView publicLookup(@Valid @RequestBody PublicTokenRequest request) {
        return consents.publicLookup(request.token());
    }

    @PostMapping("/api/v1/public/consents/sign")
    @Operation(summary = "Público: paciente assina o termo", tags = "Público")
    public PublicConsentView publicSign(@Valid @RequestBody PublicSignRequest request) {
        return consents.publicSign(
                request.token(), request.signerName(), request.signerDocument(), request.signatureImage());
    }
}
