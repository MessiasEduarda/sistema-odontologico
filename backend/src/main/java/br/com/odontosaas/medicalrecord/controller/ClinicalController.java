package br.com.odontosaas.medicalrecord.controller;

import br.com.odontosaas.medicalrecord.dto.request.ActiveRequest;
import br.com.odontosaas.medicalrecord.dto.request.AnamnesisRequest;
import br.com.odontosaas.medicalrecord.dto.request.ChartEntryRequest;
import br.com.odontosaas.medicalrecord.dto.request.DocumentRequest;
import br.com.odontosaas.medicalrecord.dto.request.DocumentUpdateRequest;
import br.com.odontosaas.medicalrecord.dto.request.FormRequest;
import br.com.odontosaas.medicalrecord.dto.request.NoteRequest;
import br.com.odontosaas.medicalrecord.dto.request.PerformedRequest;
import br.com.odontosaas.medicalrecord.dto.request.SharingRequest;
import br.com.odontosaas.medicalrecord.dto.response.AnamnesisView;
import br.com.odontosaas.medicalrecord.dto.response.ChartEntryView;
import br.com.odontosaas.medicalrecord.dto.response.DocumentView;
import br.com.odontosaas.medicalrecord.dto.response.FormView;
import br.com.odontosaas.medicalrecord.dto.response.IssuedDocument;
import br.com.odontosaas.medicalrecord.dto.response.NoteView;
import br.com.odontosaas.medicalrecord.dto.response.PerformedView;
import br.com.odontosaas.medicalrecord.dto.response.RecentAnamnesis;
import br.com.odontosaas.medicalrecord.dto.response.RecordSummary;
import br.com.odontosaas.medicalrecord.service.ClinicalService;
import br.com.odontosaas.shared.Access;
import br.com.odontosaas.shared.VoidRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
public class ClinicalController {

    private static final String READ = "hasAuthority('clinical_records:read')";
    private static final String WRITE = "hasAuthority('clinical_records:write')";
    private static final String MANAGE = "hasAuthority('clinic:settings:manage')";
    private static final String BASE = "/api/v1/patients/{patientId}";

    private final ClinicalService clinical;

    public ClinicalController(ClinicalService clinical) {
        this.clinical = clinical;
    }

    @GetMapping(BASE + "/record")
    @PreAuthorize(READ)
    @Operation(summary = "Resumo do prontuário (alertas da anamnese, contadores). Auditado.")
    public RecordSummary summary(@PathVariable UUID patientId) {
        return clinical.summary(patientId);
    }

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
            @Parameter(description = "Texto contido no nome do paciente") @RequestParam(required = false)
                    String searchTerm) {
        return clinical.recentAnamneses(kind, alertsOnly, searchTerm);
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

    @PutMapping(BASE + "/documents/{documentId}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Corrige título e texto do documento (a versão anterior fica no histórico)")
    public DocumentView updateDocument(
            @PathVariable UUID patientId,
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentUpdateRequest request) {
        return clinical.updateDocument(patientId, documentId, request);
    }

    @PostMapping(BASE + "/documents/{documentId}/void")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anula documento com motivo (não apaga; some do portal do paciente)")
    public void voidDocument(
            @PathVariable UUID patientId, @PathVariable UUID documentId, @Valid @RequestBody VoidRequest request) {
        clinical.voidDocument(patientId, documentId, request.reason());
    }

    @PutMapping(BASE + "/documents/{documentId}/sharing")
    @PreAuthorize(WRITE)
    @Operation(summary = "Mostra ou esconde o documento no portal do paciente")
    public DocumentView documentSharing(
            @PathVariable UUID patientId, @PathVariable UUID documentId, @Valid @RequestBody SharingRequest request) {
        return clinical.setDocumentSharing(patientId, documentId, request.shared());
    }

    @GetMapping("/api/v1/clinical-documents")
    @PreAuthorize(READ)
    @Operation(summary = "Documentos emitidos na clínica (filtro por tipo)")
    public List<IssuedDocument> recentDocuments(@RequestParam(required = false) String type) {
        return clinical.recentDocuments(type);
    }
}
