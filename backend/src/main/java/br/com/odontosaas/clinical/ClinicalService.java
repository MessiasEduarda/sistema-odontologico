package br.com.odontosaas.clinical;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinical.ClinicalDtos.Alert;
import br.com.odontosaas.clinical.ClinicalDtos.AnamnesisRequest;
import br.com.odontosaas.clinical.ClinicalDtos.AnamnesisView;
import br.com.odontosaas.clinical.ClinicalDtos.Answer;
import br.com.odontosaas.clinical.ClinicalDtos.ChartEntryRequest;
import br.com.odontosaas.clinical.ClinicalDtos.ChartEntryView;
import br.com.odontosaas.clinical.ClinicalDtos.DocumentRequest;
import br.com.odontosaas.clinical.ClinicalDtos.DocumentView;
import br.com.odontosaas.clinical.ClinicalDtos.FormRequest;
import br.com.odontosaas.clinical.ClinicalDtos.FormView;
import br.com.odontosaas.clinical.ClinicalDtos.IssuedDocument;
import br.com.odontosaas.clinical.ClinicalDtos.NoteRequest;
import br.com.odontosaas.clinical.ClinicalDtos.NoteView;
import br.com.odontosaas.clinical.ClinicalDtos.PerformedRequest;
import br.com.odontosaas.clinical.ClinicalDtos.PerformedView;
import br.com.odontosaas.clinical.ClinicalDtos.Question;
import br.com.odontosaas.clinical.ClinicalDtos.RecentAnamnesis;
import br.com.odontosaas.clinical.ClinicalDtos.RecordSummary;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prontuário: anamnese, odontograma, evoluções, procedimentos realizados e documentos. Registros não são editados
 * nem apagados (ver V4); toda leitura do prontuário completo é auditada.
 */
@Service
public class ClinicalService {

    /** Condições aceitas no odontograma. */
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

    private static final TypeReference<List<Question>> QUESTIONS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Answer>> ANSWERS = new TypeReference<>() {};
    private static final TypeReference<List<Alert>> ALERTS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;
    private final JsonMapper json;
    private final ApplicationEventPublisher events;
    private final ProfessionalAuthorship authorship;

    public ClinicalService(
            JdbcClient jdbc,
            TenantRefs refs,
            AuditService audit,
            JsonMapper json,
            ApplicationEventPublisher events,
            ProfessionalAuthorship authorship) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
        this.json = json;
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

    // ------------------------------------------------------------------ Resumo (abertura do prontuário)

    @Transactional
    public RecordSummary summary(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        record Basic(String fullName, LocalDate birthDate) {}
        Basic basic = jdbc.sql("select full_name, birth_date from patients where tenant_id = ? and id = ?")
                .params(tenantId, patientId)
                .query(Basic.class)
                .single();
        List<AnamnesisView> all = anamneses(patientId);
        int notes = count(
                "select count(*) from clinical_notes where tenant_id = ? and patient_id = ?", tenantId, patientId);
        int pending = count(
                "select count(*) from patient_consents where tenant_id = ? and patient_id = ? and status = 'PENDING'",
                tenantId,
                patientId);
        int attachments = count(
                "select count(*) from attachments where tenant_id = ? and patient_id = ? and deleted_at is null",
                tenantId,
                patientId);
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

    private int count(String sql, UUID tenantId, UUID patientId) {
        return jdbc.sql(sql).params(tenantId, patientId).query(Integer.class).single();
    }

    // ------------------------------------------------------------------ Fichas de anamnese

    private static final String FORM_SELECT =
            "select id, name, kind, questions::text as questions, active, updated_at from anamnesis_forms";

    private FormView mapForm(ResultSet rs) throws SQLException {
        return new FormView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("kind"),
                json.readValue(rs.getString("questions"), QUESTIONS),
                rs.getBoolean("active"),
                rs.getTimestamp("updated_at").toInstant());
    }

    /**
     * Fichas da clínica. Na primeira consulta cria a ficha odontológica padrão e, se a clínica realiza harmonização
     * orofacial, também a ficha de harmonização (ambas editáveis).
     */
    @Transactional
    public List<FormView> forms(boolean includeInactive) {
        UUID tenantId = TenantContext.requireTenantId();
        ensureDefaultForms(tenantId);
        return jdbc.sql(FORM_SELECT + " where tenant_id = ?" + (includeInactive ? "" : " and active")
                        + " order by case kind when 'DENTAL' then 0 when 'AESTHETIC' then 1 else 2 end, name")
                .param(tenantId)
                .query((rs, n) -> mapForm(rs))
                .list();
    }

    private void ensureDefaultForms(UUID tenantId) {
        record Kinds(boolean dental, boolean aesthetic, boolean aestheticsEnabled) {}
        Kinds k = jdbc.sql("""
                        select exists(select 1 from anamnesis_forms where tenant_id = ? and kind = 'DENTAL') as dental,
                               exists(select 1 from anamnesis_forms where tenant_id = ? and kind = 'AESTHETIC')
                                   as aesthetic,
                               coalesce((select aesthetics_enabled from clinic_settings where tenant_id = ?), false)
                                   as aesthetics_enabled
                        """)
                .params(tenantId, tenantId, tenantId)
                .query(Kinds.class)
                .single();
        if (!k.dental()) insertForm(tenantId, DefaultAnamnesis.DENTAL_NAME, "DENTAL", DefaultAnamnesis.QUESTIONS);
        if (k.aestheticsEnabled() && !k.aesthetic()) {
            insertForm(tenantId, DefaultAnamnesis.AESTHETIC_NAME, "AESTHETIC", DefaultAnamnesis.AESTHETIC);
        }
    }

    private void insertForm(UUID tenantId, String name, String kind, List<Question> questions) {
        jdbc.sql("""
                        insert into anamnesis_forms (id, tenant_id, name, kind, questions, updated_by)
                        values (?, ?, ?, ?, cast(? as jsonb), ?) on conflict (tenant_id, name) do nothing
                        """)
                .params(Ids.newId(), tenantId, name, kind, json.writeValueAsString(questions), actor())
                .update();
    }

    private FormView form(UUID tenantId, UUID formId) {
        return jdbc.sql(FORM_SELECT + " where tenant_id = ? and id = ?")
                .params(tenantId, formId)
                .query((rs, n) -> mapForm(rs))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Ficha de anamnese"));
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
        jdbc.sql("""
                        insert into anamnesis_forms (id, tenant_id, name, kind, questions, updated_by)
                        values (?, ?, ?, ?, cast(? as jsonb), ?)
                        """)
                .params(id, tenantId, r.name().strip(), r.kind(), json.writeValueAsString(r.questions()), actor())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "anamnesis_form", id, Map.of());
        return form(tenantId, id);
    }

    /** Alterar a ficha não afeta anamneses já preenchidas (cada uma guarda a cópia das perguntas). */
    @Transactional
    public FormView updateForm(UUID formId, FormRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        form(tenantId, formId);
        checkQuestions(r.questions());
        jdbc.sql("""
                        update anamnesis_forms set name = ?, kind = ?, questions = cast(? as jsonb), updated_by = ?,
                            updated_at = now() where tenant_id = ? and id = ?
                        """)
                .params(r.name().strip(), r.kind(), json.writeValueAsString(r.questions()), actor(), tenantId, formId)
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "anamnesis_form", formId, Map.of());
        return form(tenantId, formId);
    }

    @Transactional
    public FormView setFormActive(UUID formId, boolean active) {
        UUID tenantId = TenantContext.requireTenantId();
        form(tenantId, formId);
        jdbc.sql("update anamnesis_forms set active = ?, updated_at = now() where tenant_id = ? and id = ?")
                .params(active, tenantId, formId)
                .update();
        audit.record(
                active ? AuditAction.RECORD_UPDATED : AuditAction.RECORD_DEACTIVATED,
                "anamnesis_form",
                formId,
                Map.of());
        return form(tenantId, formId);
    }

    /** Ficha usada quando nenhuma é informada: a primeira odontológica ativa (ou a primeira ativa). */
    private FormView defaultForm() {
        List<FormView> active = forms(false);
        return active.stream()
                .filter(f -> "DENTAL".equals(f.kind()))
                .findFirst()
                .or(() -> active.stream().findFirst())
                .orElseThrow(() -> ApiException.businessRule("NO_ANAMNESIS_FORM", "Nenhuma ficha de anamnese ativa."));
    }

    // ------------------------------------------------------------------ Anamnese (preenchimentos)

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
        jdbc.sql("""
                        insert into anamneses (id, tenant_id, patient_id, form_id, form_name, form_kind, questions,
                            answers, alerts, notes, filled_by, created_by)
                        values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        form.id(),
                        form.name(),
                        form.kind(),
                        json.writeValueAsString(questions),
                        json.writeValueAsString(r.answers()),
                        json.writeValueAsString(alerts),
                        blank(r.notes()),
                        r.filledBy() == null ? "PROFESSIONAL" : r.filledBy(),
                        actor())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "anamnesis", id, Map.of("patient", patientId.toString()));
        return anamneses(patientId).stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<AnamnesisView> anamneses(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return jdbc.sql("""
                        select a.id, a.form_id, a.form_name, a.form_kind, a.questions::text as questions,
                               a.answers::text as answers, a.alerts::text as alerts, a.notes, a.filled_by,
                               u.name as created_by_name, a.created_at
                        from anamneses a left join users u on u.id = a.created_by
                        where a.tenant_id = ? and a.patient_id = ? order by a.created_at desc
                        """)
                .params(tenantId, patientId)
                .query((rs, n) -> new AnamnesisView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("form_id", UUID.class),
                        rs.getString("form_name"),
                        rs.getString("form_kind"),
                        json.readValue(rs.getString("questions"), QUESTIONS),
                        json.readValue(rs.getString("answers"), ANSWERS),
                        json.readValue(rs.getString("alerts"), ALERTS),
                        rs.getString("notes"),
                        rs.getString("filled_by"),
                        rs.getString("created_by_name"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** Preenchimentos recentes da clínica (todas as fichas), com filtros por tipo de ficha, alertas e paciente. */
    @Transactional(readOnly = true)
    public List<RecentAnamnesis> recentAnamneses(String kind, boolean alertsOnly, String query) {
        UUID tenantId = TenantContext.requireTenantId();
        String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbc.sql("""
                        select a.id, a.patient_id, p.full_name as patient_name, a.form_name, a.form_kind,
                               a.alerts::text as alerts, a.filled_by, u.name as created_by_name, a.created_at
                        from anamneses a
                        join patients p on p.tenant_id = a.tenant_id and p.id = a.patient_id
                        left join users u on u.id = a.created_by
                        where a.tenant_id = :tenant
                          and (cast(:kind as varchar) is null or a.form_kind = :kind)
                          and (not :alertsOnly or jsonb_array_length(a.alerts) > 0)
                          and (cast(:q as varchar) is null or lower(p.full_name) like :q)
                        order by a.created_at desc limit 200
                        """)
                .param("tenant", tenantId)
                .param("kind", kind == null || kind.isBlank() ? null : kind)
                .param("alertsOnly", alertsOnly)
                .param("q", q)
                .query((rs, n) -> new RecentAnamnesis(
                        rs.getObject("id", UUID.class),
                        rs.getObject("patient_id", UUID.class),
                        rs.getString("patient_name"),
                        rs.getString("form_name"),
                        rs.getString("form_kind"),
                        json.readValue(rs.getString("alerts"), ALERTS),
                        rs.getString("filled_by"),
                        rs.getString("created_by_name"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /**
     * Alertas vigentes de cada ficha (odontológica, harmonização...), por pergunta: vale a resposta mais recente que
     * foi de fato dada ("SIM" ou "NÃO"). Pergunta deixada em branco numa ficha nova NÃO apaga o alerta anterior; só um
     * "NÃO" explícito o resolve. As fichas são agrupadas pelo id (renomear a ficha não separa o histórico) e só contam
     * as perguntas da versão mais recente dela. {@code all} vem do mais novo para o mais antigo.
     */
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

    // ------------------------------------------------------------------ Odontograma

    @Transactional(readOnly = true)
    public List<ChartEntryView> chart(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return jdbc.sql("""
                        select e.id, e.tooth, e.surfaces, e.condition, e.status, e.notes, u.name as created_by_name,
                               e.created_at, e.voided_at, e.void_reason
                        from dental_chart_entries e left join users u on u.id = e.created_by
                        where e.tenant_id = ? and e.patient_id = ? order by e.created_at
                        """)
                .params(tenantId, patientId)
                .query(ChartEntryView.class)
                .list();
    }

    @Transactional
    public ChartEntryView addChartEntry(UUID patientId, ChartEntryRequest r) {
        UUID tenantId = tenantFor(patientId);
        if (!isValidTooth(r.tooth())) throw ApiException.invalidField("tooth", "Dente inválido (notação FDI).");
        if (!CONDITIONS.contains(r.condition())) throw ApiException.invalidField("condition", "Condição inválida.");
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into dental_chart_entries (id, tenant_id, patient_id, tooth, surfaces, condition, status,
                            notes, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        r.tooth(),
                        blank(r.surfaces()),
                        r.condition(),
                        r.status(),
                        blank(r.notes()),
                        actor())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "dental_chart_entry", id, Map.of());
        return chart(patientId).stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void voidChartEntry(UUID patientId, UUID entryId, String reason) {
        UUID tenantId = tenantFor(patientId);
        int updated = jdbc.sql("""
                        update dental_chart_entries set voided_at = now(), voided_by = ?, void_reason = ?
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        """)
                .params(actor(), reason.strip(), tenantId, patientId, entryId)
                .update();
        if (updated == 0) throw ApiException.notFound("Registro do odontograma");
        audit.record(AuditAction.RECORD_VOIDED, "dental_chart_entry", entryId, Map.of());
    }

    static boolean isValidTooth(int t) {
        int quadrant = t / 10;
        int tooth = t % 10;
        return (quadrant >= 1 && quadrant <= 4 && tooth >= 1 && tooth <= 8)
                || (quadrant >= 5 && quadrant <= 8 && tooth >= 1 && tooth <= 5);
    }

    // ------------------------------------------------------------------ Evoluções

    @Transactional(readOnly = true)
    public List<NoteView> notes(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return jdbc.sql("""
                        select n.id, n.content, n.professional_id, p.name as professional_name, n.appointment_id,
                               n.amends_note_id, u.name as created_by_name, n.created_at
                        from clinical_notes n
                        left join professionals p on p.tenant_id = n.tenant_id and p.id = n.professional_id
                        left join users u on u.id = n.created_by
                        where n.tenant_id = ? and n.patient_id = ? order by n.created_at desc
                        """).params(tenantId, patientId).query(NoteView.class).list();
    }

    @Transactional
    public NoteView addNote(UUID patientId, NoteRequest r) {
        UUID tenantId = tenantFor(patientId);
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        authorship.require(tenantId, r.professionalId());
        refs.require("appointments", tenantId, r.appointmentId(), "Agendamento");
        if (r.amendsNoteId() != null) {
            boolean samePatient = jdbc.sql(
                            "select exists(select 1 from clinical_notes where tenant_id = ? and id = ? and patient_id = ?)")
                    .params(tenantId, r.amendsNoteId(), patientId)
                    .query(Boolean.class)
                    .single();
            if (!samePatient) throw ApiException.notFound("Evolução a retificar");
        }
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into clinical_notes (id, tenant_id, patient_id, professional_id, appointment_id, content,
                            amends_note_id, created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        r.professionalId(),
                        r.appointmentId(),
                        r.content().strip(),
                        r.amendsNoteId(),
                        actor())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "clinical_note", id, Map.of());
        return notes(patientId).stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------------------ Procedimentos realizados

    @Transactional(readOnly = true)
    public List<PerformedView> performed(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return jdbc.sql("""
                        select x.id, x.professional_id, p.name as professional_name, x.procedure_id, x.description,
                               x.tooth, x.surfaces, x.treatment_item_id, x.performed_at, x.notes, x.voided_at,
                               x.void_reason
                        from performed_procedures x
                        join professionals p on p.tenant_id = x.tenant_id and p.id = x.professional_id
                        where x.tenant_id = ? and x.patient_id = ? order by x.performed_at desc
                        """)
                .params(tenantId, patientId)
                .query(PerformedView.class)
                .list();
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
            boolean ok = jdbc.sql("""
                            select exists(select 1 from treatment_plan_items i
                                join treatment_plans t on t.tenant_id = i.tenant_id and t.id = i.plan_id
                                where i.tenant_id = ? and i.id = ? and t.patient_id = ? and i.status = 'PLANNED'
                                  and t.status in ('APPROVED'))
                            """)
                    .params(tenantId, r.treatmentItemId(), patientId)
                    .query(Boolean.class)
                    .single();
            if (!ok) {
                throw ApiException.businessRule(
                        "INVALID_TREATMENT_ITEM", "O item do plano não existe, não está aprovado ou já foi realizado.");
            }
        }
        if (r.performedAt() != null && r.performedAt().isAfter(Instant.now().plusSeconds(300))) {
            throw ApiException.invalidField("performedAt", "A data de realização não pode estar no futuro.");
        }
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into performed_procedures (id, tenant_id, patient_id, professional_id, procedure_id,
                            description, tooth, surfaces, treatment_item_id, performed_at, notes, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        r.professionalId(),
                        r.procedureId(),
                        r.description().strip(),
                        r.tooth(),
                        blank(r.surfaces()),
                        r.treatmentItemId(),
                        Timestamp.from(r.performedAt() == null ? Instant.now() : r.performedAt()),
                        blank(r.notes()),
                        actor())
                .update();
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
        UUID itemId = jdbc.sql("""
                        update performed_procedures set voided_at = now(), voided_by = ?, void_reason = ?
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        returning treatment_item_id
                        """)
                .params(actor(), reason.strip(), tenantId, patientId, performedId)
                .query((rs, n) -> Optional.ofNullable(rs.getObject(1, UUID.class)))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Procedimento realizado"))
                .orElse(null);
        audit.record(AuditAction.RECORD_VOIDED, "performed_procedure", performedId, Map.of());
        if (itemId != null) events.publishEvent(new ProcedurePerformed(tenantId, itemId, true));
    }

    // ------------------------------------------------------------------ Documentos

    @Transactional(readOnly = true)
    public List<DocumentView> documents(UUID patientId) {
        UUID tenantId = tenantFor(patientId);
        return jdbc.sql("""
                        select d.id, d.professional_id, p.name as professional_name,
                               case when p.cro is not null then 'CRO-' || coalesce(p.cro_state, '') || ' ' || p.cro end
                                   as professional_cro,
                               d.type, d.title, d.content, d.issued_at
                        from clinical_documents d
                        join professionals p on p.tenant_id = d.tenant_id and p.id = d.professional_id
                        where d.tenant_id = ? and d.patient_id = ? order by d.issued_at desc
                        """)
                .params(tenantId, patientId)
                .query(DocumentView.class)
                .list();
    }

    @Transactional
    public DocumentView addDocument(UUID patientId, DocumentRequest r) {
        UUID tenantId = tenantFor(patientId);
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        authorship.require(tenantId, r.professionalId());
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into clinical_documents (id, tenant_id, patient_id, professional_id, type, title, content,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        r.professionalId(),
                        r.type(),
                        r.title().strip(),
                        r.content().strip(),
                        actor())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "clinical_document", id, Map.of("type", r.type()));
        return documents(patientId).stream()
                .filter(d -> d.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    /** Documentos emitidos na clínica (todas as fichas), com filtro de tipo. */
    @Transactional(readOnly = true)
    public List<IssuedDocument> recentDocuments(String type) {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.sql("""
                        select d.id, d.patient_id, pa.full_name as patient_name, p.name as professional_name, d.type,
                               d.title, d.issued_at
                        from clinical_documents d
                        join patients pa on pa.tenant_id = d.tenant_id and pa.id = d.patient_id
                        join professionals p on p.tenant_id = d.tenant_id and p.id = d.professional_id
                        where d.tenant_id = :tenant and (cast(:type as varchar) is null or d.type = :type)
                        order by d.issued_at desc limit 200
                        """)
                .param("tenant", tenantId)
                .param("type", type == null || type.isBlank() ? null : type)
                .query(IssuedDocument.class)
                .list();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
