package br.com.odontosaas.medicalrecord.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinic.ProfessionalAuthorship;
import br.com.odontosaas.medicalrecord.dto.request.AnamnesisRequest;
import br.com.odontosaas.medicalrecord.dto.request.Answer;
import br.com.odontosaas.medicalrecord.dto.request.ChartEntryRequest;
import br.com.odontosaas.medicalrecord.dto.request.DocumentRequest;
import br.com.odontosaas.medicalrecord.dto.request.DocumentUpdateRequest;
import br.com.odontosaas.medicalrecord.dto.request.FormRequest;
import br.com.odontosaas.medicalrecord.dto.request.NoteRequest;
import br.com.odontosaas.medicalrecord.dto.request.PerformedRequest;
import br.com.odontosaas.medicalrecord.dto.request.Question;
import br.com.odontosaas.medicalrecord.dto.response.Alert;
import br.com.odontosaas.medicalrecord.dto.response.AnamnesisView;
import br.com.odontosaas.medicalrecord.dto.response.ChartEntryView;
import br.com.odontosaas.medicalrecord.dto.response.DocumentView;
import br.com.odontosaas.medicalrecord.dto.response.FormView;
import br.com.odontosaas.medicalrecord.dto.response.IssuedDocument;
import br.com.odontosaas.medicalrecord.dto.response.NoteView;
import br.com.odontosaas.medicalrecord.dto.response.PerformedView;
import br.com.odontosaas.medicalrecord.dto.response.RecentAnamnesis;
import br.com.odontosaas.medicalrecord.dto.response.RecordSummary;
import br.com.odontosaas.medicalrecord.event.ProcedurePerformed;
import br.com.odontosaas.medicalrecord.repository.AnamnesisFormRepository;
import br.com.odontosaas.medicalrecord.repository.AnamnesisFormRepository.FormKindsRow;
import br.com.odontosaas.medicalrecord.repository.AnamnesisRepository;
import br.com.odontosaas.medicalrecord.repository.ChartRepository;
import br.com.odontosaas.medicalrecord.repository.ClinicalDocumentRepository;
import br.com.odontosaas.medicalrecord.repository.ClinicalNoteRepository;
import br.com.odontosaas.medicalrecord.repository.PerformedProcedureRepository;
import br.com.odontosaas.medicalrecord.repository.RecordSummaryRepository;
import br.com.odontosaas.medicalrecord.repository.RecordSummaryRepository.PatientBasicRow;
import br.com.odontosaas.medicalrecord.support.DefaultAnamnesis;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalService {

    static final Set<String> CONDITIONS = Set.of(
            "CARIES",
            "RESTORATION",
            "MISSING",
            "EXTRACTION",
            "CROWN",
            "ROOT_CANAL",
            "IMPLANT",
            "SEALANT",
            "FRACTURE",
            "PROSTHESIS",
            "MOBILITY",
            "OTHER");

    private final RecordSummaryRepository summaries;
    private final AnamnesisFormRepository forms;
    private final AnamnesisRepository anamnesisRecords;
    private final ChartRepository charts;
    private final ClinicalNoteRepository clinicalNotes;
    private final PerformedProcedureRepository performedProcedures;
    private final ClinicalDocumentRepository clinicalDocuments;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ProfessionalAuthorship authorship;

    public ClinicalService(
            RecordSummaryRepository summaries,
            AnamnesisFormRepository forms,
            AnamnesisRepository anamnesisRecords,
            ChartRepository charts,
            ClinicalNoteRepository clinicalNotes,
            PerformedProcedureRepository performedProcedures,
            ClinicalDocumentRepository clinicalDocuments,
            TenantRefs refs,
            AuditService audit,
            ApplicationEventPublisher events,
            ProfessionalAuthorship authorship) {
        this.summaries = summaries;
        this.forms = forms;
        this.anamnesisRecords = anamnesisRecords;
        this.charts = charts;
        this.clinicalNotes = clinicalNotes;
        this.performedProcedures = performedProcedures;
        this.clinicalDocuments = clinicalDocuments;
        this.refs = refs;
        this.audit = audit;
        this.events = events;
        this.authorship = authorship;
    }

    private UUID tenantFor(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        return tenantId;
    }

    private static UUID actor() {
        return TenantContext.userId().orElse(null);
    }

    @Transactional
    public RecordSummary summary(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        PatientBasicRow basic = summaries.findPatientBasic(tenantId, patientId);
        List<AnamnesisView> all = anamneses(patientId);
        int notes = summaries.countNotes(tenantId, patientId);
        int pending = summaries.countPendingConsents(tenantId, patientId);
        int attachments = summaries.countAttachments(tenantId, patientId);
        audit.record(AuditAction.CLINICAL_RECORD_VIEWED, "patient", patientId, Map.of());
        return new RecordSummary(
                patientId,
                basic.fullName(),
                basic.birthDate(),
                currentAlerts(all),
                all.isEmpty() ? null : all.getFirst().createdAt(),
                notes,
                pending,
                attachments);
    }

    @Transactional
    public List<FormView> forms(boolean includeInactive) {
        UUID tenantId = TenantContext.requireTenantId();
        ensureDefaultForms(tenantId);
        return forms.findAll(tenantId, includeInactive);
    }

    private void ensureDefaultForms(UUID tenantId) {
        FormKindsRow k = forms.findKinds(tenantId);
        if (!k.dental()) insertForm(tenantId, DefaultAnamnesis.DENTAL_NAME, "DENTAL", DefaultAnamnesis.QUESTIONS);
        if (k.aestheticsEnabled() && !k.aesthetic()) {
            insertForm(tenantId, DefaultAnamnesis.AESTHETIC_NAME, "AESTHETIC", DefaultAnamnesis.AESTHETIC);
        }
    }

    private void insertForm(UUID tenantId, String name, String kind, List<Question> questions) {
        forms.insertIfAbsent(Ids.newId(), tenantId, name, kind, questions, actor());
    }

    private FormView form(UUID tenantId, UUID formId) {
        return forms.find(tenantId, formId).orElseThrow(() -> ApiException.notFound("Ficha de anamnese"));
    }

    private static void checkQuestions(List<Question> questions) {
        Set<String> ids = new HashSet<>();
        for (Question q : questions) {
            if (!ids.add(q.id())) throw ApiException.invalidField("questions", "Identificador repetido: " + q.id());
        }
    }

    @Transactional
    public FormView createForm(FormRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        checkQuestions(r.questions());
        UUID id = Ids.newId();
        forms.insert(id, tenantId, r.name().strip(), r.kind(), r.questions(), actor());
        audit.record(AuditAction.RECORD_CREATED, "anamnesis_form", id, Map.of());
        return form(tenantId, id);
    }

    @Transactional
    public FormView updateForm(UUID formId, FormRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        form(tenantId, formId);
        checkQuestions(r.questions());
        forms.update(tenantId, formId, r.name().strip(), r.kind(), r.questions(), actor());
        audit.record(AuditAction.RECORD_UPDATED, "anamnesis_form", formId, Map.of());
        return form(tenantId, formId);
    }

    @Transactional
    public FormView setFormActive(UUID formId, boolean active) {
        UUID tenantId = TenantContext.requireTenantId();
        form(tenantId, formId);
        forms.setActive(tenantId, formId, active);
        audit.record(
                active ? AuditAction.RECORD_UPDATED : AuditAction.RECORD_DEACTIVATED,
                "anamnesis_form",
                formId,
                Map.of());
        return form(tenantId, formId);
    }

    private FormView defaultForm() {
        List<FormView> active = forms(false);
        return active.stream()
                .filter(f -> "DENTAL".equals(f.kind()))
                .findFirst()
                .or(() -> active.stream().findFirst())
                .orElseThrow(() -> ApiException.businessRule("NO_ANAMNESIS_FORM", "Nenhuma ficha de anamnese ativa."));
    }

    @Transactional
    public AnamnesisView fillAnamnesis(UUID patientId, AnamnesisRequest r) {
        UUID tenantId = tenantFor(patientId);
        FormView form = r.formId() == null ? defaultForm() : form(tenantId, r.formId());
        if (!form.active()) throw ApiException.businessRule("FORM_INACTIVE", "Esta ficha está desativada.");
        List<Question> questions = form.questions();
        Set<String> known = new HashSet<>();
        questions.forEach(q -> known.add(q.id()));
        for (String key : r.answers().keySet()) {
            if (!known.contains(key)) throw ApiException.invalidField("answers", "Pergunta desconhecida: " + key);
        }
        List<Alert> alerts = new ArrayList<>();
        for (Question q : questions) {
            Answer a = r.answers().get(q.id());
            if (q.alertOnYes() && a != null && "YES".equals(a.answer())) {
                alerts.add(new Alert(q.id(), q.text(), a.detail()));
            }
        }
        UUID id = Ids.newId();
        anamnesisRecords.insert(
                id,
                tenantId,
                patientId,
                form.id(),
                form.name(),
                form.kind(),
                questions,
                r.answers(),
                alerts,
                blank(r.notes()),
                r.filledBy() == null ? "PROFESSIONAL" : r.filledBy(),
                actor());
        audit.record(AuditAction.RECORD_CREATED, "anamnesis", id, Map.of("patient", patientId.toString()));
        return anamneses(patientId).stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<AnamnesisView> anamneses(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return anamnesisRecords.findByPatient(tenantId, patientId);
    }

    @Transactional(readOnly = true)
    public List<RecentAnamnesis> recentAnamneses(String kind, boolean alertsOnly, String query) {
        UUID tenantId = TenantContext.requireTenantId();
        String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return anamnesisRecords.findRecent(tenantId, kind == null || kind.isBlank() ? null : kind, alertsOnly, q);
    }

    private static List<Alert> currentAlerts(List<AnamnesisView> all) {
        Map<Object, List<AnamnesisView>> byForm = new LinkedHashMap<>();
        for (AnamnesisView a : all) {
            Object key = a.formId() != null ? a.formId() : a.formName();
            byForm.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        List<Alert> alerts = new ArrayList<>();
        for (List<AnamnesisView> fills : byForm.values()) {
            for (Question q : fills.getFirst().questions()) {
                if (!q.alertOnYes()) continue;
                for (AnamnesisView fill : fills) {
                    Answer given = fill.answers().get(q.id());
                    if (given == null || given.answer() == null) continue;
                    if ("YES".equals(given.answer())) alerts.add(new Alert(q.id(), q.text(), given.detail()));
                    break;
                }
            }
        }
        return alerts;
    }

    @Transactional(readOnly = true)
    public List<ChartEntryView> chart(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return charts.findByPatient(tenantId, patientId);
    }

    @Transactional
    public ChartEntryView addChartEntry(UUID patientId, ChartEntryRequest r) {
        UUID tenantId = tenantFor(patientId);
        if (!isValidTooth(r.tooth())) throw ApiException.invalidField("tooth", "Dente inválido (notação FDI).");
        if (!CONDITIONS.contains(r.condition())) throw ApiException.invalidField("condition", "Condição inválida.");
        UUID id = Ids.newId();
        charts.insert(
                id,
                tenantId,
                patientId,
                r.tooth(),
                blank(r.surfaces()),
                r.condition(),
                r.status(),
                blank(r.notes()),
                actor());
        audit.record(AuditAction.RECORD_CREATED, "dental_chart_entry", id, Map.of());
        return chart(patientId).stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void voidChartEntry(UUID patientId, UUID entryId, String reason) {
        UUID tenantId = tenantFor(patientId);
        int updated = charts.voidEntry(tenantId, patientId, entryId, actor(), reason.strip());
        if (updated == 0) throw ApiException.notFound("Registro do odontograma");
        audit.record(AuditAction.RECORD_VOIDED, "dental_chart_entry", entryId, Map.of());
    }

    static boolean isValidTooth(int t) {
        int quadrant = t / 10;
        int tooth = t % 10;
        return (quadrant >= 1 && quadrant <= 4 && tooth >= 1 && tooth <= 8)
                || (quadrant >= 5 && quadrant <= 8 && tooth >= 1 && tooth <= 5);
    }

    @Transactional(readOnly = true)
    public List<NoteView> notes(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return clinicalNotes.findByPatient(tenantId, patientId);
    }

    @Transactional
    public NoteView addNote(UUID patientId, NoteRequest r) {
        UUID tenantId = tenantFor(patientId);
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        authorship.require(tenantId, r.professionalId());
        refs.require("appointments", tenantId, r.appointmentId(), "Agendamento");
        if (r.amendsNoteId() != null) {
            boolean samePatient = clinicalNotes.belongsToPatient(tenantId, r.amendsNoteId(), patientId);
            if (!samePatient) throw ApiException.notFound("Evolução a retificar");
        }
        UUID id = Ids.newId();
        clinicalNotes.insert(
                id,
                tenantId,
                patientId,
                r.professionalId(),
                r.appointmentId(),
                r.content().strip(),
                r.amendsNoteId(),
                actor());
        audit.record(AuditAction.RECORD_CREATED, "clinical_note", id, Map.of());
        return notes(patientId).stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<PerformedView> performed(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return performedProcedures.findByPatient(tenantId, patientId);
    }

    @Transactional
    public PerformedView addPerformed(UUID patientId, PerformedRequest r) {
        UUID tenantId = tenantFor(patientId);
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        authorship.require(tenantId, r.professionalId());
        refs.require("procedures", tenantId, r.procedureId(), "Procedimento");
        if (r.tooth() != null && !isValidTooth(r.tooth())) {
            throw ApiException.invalidField("tooth", "Dente inválido (notação FDI).");
        }
        if (r.treatmentItemId() != null) {
            boolean ok = performedProcedures.isApprovedPlannedItem(tenantId, r.treatmentItemId(), patientId);
            if (!ok) {
                throw ApiException.businessRule(
                        "INVALID_TREATMENT_ITEM", "O item do plano não existe, não está aprovado ou já foi realizado.");
            }
        }
        if (r.performedAt() != null && r.performedAt().isAfter(Instant.now().plusSeconds(300))) {
            throw ApiException.invalidField("performedAt", "A data de realização não pode estar no futuro.");
        }
        UUID id = Ids.newId();
        performedProcedures.insert(
                id,
                tenantId,
                patientId,
                r.professionalId(),
                r.procedureId(),
                r.description().strip(),
                r.tooth(),
                blank(r.surfaces()),
                r.treatmentItemId(),
                r.performedAt() == null ? Instant.now() : r.performedAt(),
                blank(r.notes()),
                actor());
        audit.record(AuditAction.RECORD_CREATED, "performed_procedure", id, Map.of());
        if (r.treatmentItemId() != null) {
            events.publishEvent(new ProcedurePerformed(tenantId, r.treatmentItemId(), false));
        }
        return performed(patientId).stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void voidPerformed(UUID patientId, UUID performedId, String reason) {
        UUID tenantId = tenantFor(patientId);
        UUID itemId = performedProcedures
                .voidReturningItem(tenantId, patientId, performedId, actor(), reason.strip())
                .orElseThrow(() -> ApiException.notFound("Procedimento realizado"))
                .treatmentItemId();
        audit.record(AuditAction.RECORD_VOIDED, "performed_procedure", performedId, Map.of());
        if (itemId != null) events.publishEvent(new ProcedurePerformed(tenantId, itemId, true));
    }

    @Transactional(readOnly = true)
    public List<DocumentView> documents(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return clinicalDocuments.findByPatient(tenantId, patientId);
    }

    @Transactional
    public DocumentView addDocument(UUID patientId, DocumentRequest r) {
        UUID tenantId = tenantFor(patientId);
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        authorship.require(tenantId, r.professionalId());
        UUID id = Ids.newId();
        clinicalDocuments.insert(
                id,
                tenantId,
                patientId,
                r.professionalId(),
                r.type(),
                r.title().strip(),
                r.content().strip(),
                Boolean.TRUE.equals(r.sharedWithPatient()),
                actor());
        audit.record(AuditAction.RECORD_CREATED, "clinical_document", id, Map.of("type", r.type()));
        return document(patientId, id);
    }

    @Transactional
    public DocumentView updateDocument(UUID patientId, UUID documentId, DocumentUpdateRequest r) {
        UUID tenantId = tenantFor(patientId);
        ClinicalDocumentRepository.DocumentHeadRow head = clinicalDocuments
                .findHead(tenantId, patientId, documentId)
                .orElseThrow(() -> ApiException.notFound("Documento"));
        if (head.voided()) {
            throw ApiException.conflict("DOCUMENT_VOIDED", "Documento anulado não pode ser corrigido.");
        }
        authorship.require(tenantId, head.professionalId());
        clinicalDocuments.insertRevision(Ids.newId(), tenantId, documentId, head.title(), head.content(), actor());
        clinicalDocuments.updateContent(
                tenantId, documentId, r.title().strip(), r.content().strip(), actor());
        audit.record(AuditAction.RECORD_UPDATED, "clinical_document", documentId, Map.of());
        return document(patientId, documentId);
    }

    @Transactional
    public void voidDocument(UUID patientId, UUID documentId, String reason) {
        UUID tenantId = tenantFor(patientId);
        ClinicalDocumentRepository.DocumentHeadRow head = clinicalDocuments
                .findHead(tenantId, patientId, documentId)
                .orElseThrow(() -> ApiException.notFound("Documento"));
        authorship.require(tenantId, head.professionalId());
        if (clinicalDocuments.voidDocument(tenantId, patientId, documentId, reason.strip(), actor()) == 0) {
            throw ApiException.conflict("DOCUMENT_VOIDED", "Este documento já foi anulado.");
        }
        audit.record(AuditAction.RECORD_VOIDED, "clinical_document", documentId, Map.of());
    }

    @Transactional
    public DocumentView setDocumentSharing(UUID patientId, UUID documentId, boolean shared) {
        UUID tenantId = tenantFor(patientId);
        if (clinicalDocuments.updateSharing(tenantId, patientId, documentId, shared) == 0) {
            throw ApiException.notFound("Documento");
        }
        audit.record(AuditAction.RECORD_UPDATED, "clinical_document", documentId, Map.of("sharedWithPatient", shared));
        return document(patientId, documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> sharedDocuments(UUID patientId) {
        return documents(patientId).stream()
                .filter(d -> d.sharedWithPatient() && d.voidedAt() == null)
                .toList();
    }

    private DocumentView document(UUID patientId, UUID documentId) {
        return documents(patientId).stream()
                .filter(d -> d.id().equals(documentId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Documento"));
    }

    @Transactional(readOnly = true)
    public List<IssuedDocument> recentDocuments(String type) {
        UUID tenantId = TenantContext.requireTenantId();
        return clinicalDocuments.findRecent(tenantId, type == null || type.isBlank() ? null : type);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
