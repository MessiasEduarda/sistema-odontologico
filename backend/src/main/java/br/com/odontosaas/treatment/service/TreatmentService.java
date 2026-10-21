package br.com.odontosaas.treatment.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinical.ProcedurePerformed;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import br.com.odontosaas.treatment.dto.request.ApproveRequest;
import br.com.odontosaas.treatment.dto.request.ItemRequest;
import br.com.odontosaas.treatment.dto.request.PlanRequest;
import br.com.odontosaas.treatment.dto.response.ItemView;
import br.com.odontosaas.treatment.dto.response.PlanSummary;
import br.com.odontosaas.treatment.dto.response.PlanView;
import br.com.odontosaas.treatment.event.TreatmentPlanApproved;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planos de tratamento / orçamentos. Fluxo: DRAFT → PROPOSED → APPROVED → COMPLETED (ou REJECTED/CANCELLED).
 * Valores e totais são sempre calculados aqui; o cliente nunca envia totais.
 */
@Service
public class TreatmentService {

    private static final Set<String> EDITABLE = Set.of("DRAFT", "PROPOSED");

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public TreatmentService(JdbcClient jdbc, TenantRefs refs, AuditService audit, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list(UUID patientId, String status) {
        UUID tenantId = TenantContext.requireTenantId();
        return jdbc.sql("""
                        select t.id, t.patient_id, p.full_name as patient_name, t.professional_id,
                               pr.name as professional_name, t.title, t.status,
                               greatest(coalesce(sum(case when i.status <> 'CANCELLED'
                                   then i.quantity * i.unit_price - i.discount end), 0) - t.discount, 0) as total,
                               count(i.id) filter (where i.status <> 'CANCELLED') as items_total,
                               count(i.id) filter (where i.status = 'DONE') as items_done,
                               t.created_at, t.approved_at
                        from treatment_plans t
                        join patients p on p.tenant_id = t.tenant_id and p.id = t.patient_id
                        join professionals pr on pr.tenant_id = t.tenant_id and pr.id = t.professional_id
                        left join treatment_plan_items i on i.tenant_id = t.tenant_id and i.plan_id = t.id
                        where t.tenant_id = ? and (cast(? as uuid) is null or t.patient_id = ?)
                          and (cast(? as text) is null or t.status = ?)
                        group by t.id, p.full_name, pr.name
                        order by t.created_at desc limit 300
                        """)
                .params(tenantId, patientId, patientId, blank(status), blank(status))
                .query(PlanSummary.class)
                .list();
    }

    @Transactional(readOnly = true)
    public PlanView get(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        record Head(
                UUID id,
                UUID patientId,
                String patientName,
                UUID professionalId,
                String professionalName,
                String title,
                String status,
                BigDecimal discount,
                String paymentConditions,
                String notes,
                LocalDate validUntil,
                java.time.Instant approvedAt,
                String approvedByName,
                java.time.Instant createdAt,
                long version,
                UUID receivableId) {}
        Head h = jdbc.sql("""
                        select t.id, t.patient_id, p.full_name as patient_name, t.professional_id,
                               pr.name as professional_name, t.title, t.status, t.discount, t.payment_conditions,
                               t.notes, t.valid_until, t.approved_at, t.approved_by_name, t.created_at, t.version,
                               (select r.id from receivables r where r.tenant_id = t.tenant_id
                                   and r.treatment_plan_id = t.id and r.status <> 'CANCELLED' limit 1)
                                   as receivable_id
                        from treatment_plans t
                        join patients p on p.tenant_id = t.tenant_id and p.id = t.patient_id
                        join professionals pr on pr.tenant_id = t.tenant_id and pr.id = t.professional_id
                        where t.tenant_id = ? and t.id = ?
                        """)
                .params(tenantId, id)
                .query(Head.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Plano de tratamento"));
        List<ItemView> items =
                jdbc.sql("""
                        select id, procedure_id, description, tooth, surfaces, quantity, unit_price, discount,
                               quantity * unit_price - discount as total, status, done_at
                        from treatment_plan_items where tenant_id = ? and plan_id = ? order by sort_order
                        """).params(tenantId, id).query(ItemView.class).list();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal itemsDiscount = BigDecimal.ZERO;
        for (ItemView i : items) {
            if ("CANCELLED".equals(i.status())) continue;
            subtotal = subtotal.add(i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())));
            itemsDiscount = itemsDiscount.add(i.discount());
        }
        BigDecimal total =
                subtotal.subtract(itemsDiscount).subtract(h.discount()).max(BigDecimal.ZERO);
        return new PlanView(
                h.id(),
                h.patientId(),
                h.patientName(),
                h.professionalId(),
                h.professionalName(),
                h.title(),
                h.status(),
                subtotal,
                itemsDiscount,
                h.discount(),
                total,
                h.paymentConditions(),
                h.notes(),
                h.validUntil(),
                h.approvedAt(),
                h.approvedByName(),
                h.createdAt(),
                h.version(),
                h.receivableId(),
                items);
    }

    @Transactional
    public PlanView create(PlanRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        validate(tenantId, r);
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into treatment_plans (id, tenant_id, patient_id, professional_id, title, discount,
                            payment_conditions, notes, valid_until, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.patientId(),
                        r.professionalId(),
                        r.title().strip(),
                        zero(r.discount()),
                        blank(r.paymentConditions()),
                        blank(r.notes()),
                        r.validUntil(),
                        TenantContext.userId().orElse(null))
                .update();
        insertItems(tenantId, id, r.items());
        ensureNonNegative(id);
        audit.record(AuditAction.RECORD_CREATED, "treatment_plan", id, Map.of());
        return get(id);
    }

    @Transactional
    public PlanView update(UUID id, PlanRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        PlanView current = get(id);
        if (!EDITABLE.contains(current.status())) {
            throw ApiException.businessRule(
                    "PLAN_LOCKED", "Somente orçamentos em rascunho ou apresentados podem ser editados.");
        }
        if (!current.patientId().equals(r.patientId())) {
            throw ApiException.invalidField("patientId", "Não é possível trocar o paciente de um orçamento.");
        }
        validate(tenantId, r);
        int updated = jdbc.sql("""
                        update treatment_plans set professional_id = ?, title = ?, discount = ?,
                            payment_conditions = ?, notes = ?, valid_until = ?, updated_at = now(),
                            version = version + 1
                        where tenant_id = ? and id = ? and (cast(? as bigint) is null or version = ?)
                        """)
                .params(
                        r.professionalId(),
                        r.title().strip(),
                        zero(r.discount()),
                        blank(r.paymentConditions()),
                        blank(r.notes()),
                        r.validUntil(),
                        tenantId,
                        id,
                        r.version(),
                        r.version())
                .update();
        Versioned.check(updated, true, "Plano de tratamento");
        jdbc.sql("delete from treatment_plan_items where tenant_id = ? and plan_id = ?")
                .params(tenantId, id)
                .update();
        insertItems(tenantId, id, r.items());
        ensureNonNegative(id);
        audit.record(AuditAction.RECORD_UPDATED, "treatment_plan", id, Map.of());
        return get(id);
    }

    @Transactional
    public PlanView propose(UUID id) {
        return transition(id, Set.of("DRAFT"), "PROPOSED", null);
    }

    @Transactional
    public PlanView reject(UUID id, String reason) {
        return transition(id, Set.of("DRAFT", "PROPOSED"), "REJECTED", reason);
    }

    @Transactional
    public PlanView cancel(UUID id, String reason) {
        PlanView plan = get(id);
        if (plan.receivableId() != null) {
            throw ApiException.businessRule(
                    "PLAN_HAS_RECEIVABLE", "Cancele antes a conta a receber gerada por este orçamento no Financeiro.");
        }
        return transition(id, Set.of("DRAFT", "PROPOSED", "APPROVED"), "CANCELLED", reason);
    }

    /** Aprovação do paciente. Opcionalmente gera a conta a receber parcelada (via evento para o Financeiro). */
    @Transactional
    public PlanView approve(UUID id, ApproveRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        PlanView plan = get(id);
        if (!EDITABLE.contains(plan.status())) {
            throw ApiException.businessRule("INVALID_PLAN_STATUS", "Este orçamento não pode ser aprovado.");
        }
        if (plan.validUntil() != null && plan.validUntil().isBefore(LocalDate.now())) {
            throw ApiException.businessRule("PLAN_EXPIRED", "A validade deste orçamento já passou. Atualize-o.");
        }
        int updated = jdbc.sql("""
                        update treatment_plans set status = 'APPROVED', approved_at = now(), approved_by_name = ?,
                            updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ? and version = ?
                        """)
                .params(r.approvedByName().strip(), tenantId, id, plan.version())
                .update();
        Versioned.check(updated, true, "Plano de tratamento");
        audit.record(AuditAction.RECORD_UPDATED, "treatment_plan", id, Map.of("status", "APPROVED"));
        if (r.generateReceivable() && plan.total().signum() > 0) {
            events.publishEvent(new TreatmentPlanApproved(
                    tenantId,
                    id,
                    plan.patientId(),
                    "Tratamento: " + plan.title(),
                    plan.total(),
                    r.installments(),
                    r.firstDueDate() == null ? LocalDate.now() : r.firstDueDate()));
        }
        return get(id);
    }

    /** Baixa (ou reabre) o item quando o procedimento é registrado (ou anulado) no prontuário. */
    @EventListener
    public void onProcedurePerformed(ProcedurePerformed event) {
        jdbc.sql("""
                        update treatment_plan_items set status = ?, done_at = case when ? then null else now() end
                        where tenant_id = ? and id = ?
                        """)
                .params(event.voided() ? "PLANNED" : "DONE", event.voided(), event.tenantId(), event.treatmentItemId())
                .update();
        UUID planId = jdbc.sql("select plan_id from treatment_plan_items where tenant_id = ? and id = ?")
                .params(event.tenantId(), event.treatmentItemId())
                .query(UUID.class)
                .single();
        // Plano aprovado com todos os itens realizados (ou cancelados) passa a "concluído", e volta se reaberto.
        jdbc.sql("""
                        update treatment_plans t set status = case
                                when not exists (select 1 from treatment_plan_items i where i.tenant_id = t.tenant_id
                                    and i.plan_id = t.id and i.status = 'PLANNED') then 'COMPLETED' else 'APPROVED' end,
                            updated_at = now(), version = version + 1
                        where t.tenant_id = ? and t.id = ? and t.status in ('APPROVED', 'COMPLETED')
                        """).params(event.tenantId(), planId).update();
    }

    private PlanView transition(UUID id, Set<String> from, String to, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        PlanView plan = get(id);
        if (!from.contains(plan.status())) {
            throw ApiException.businessRule("INVALID_PLAN_STATUS", "Ação não permitida no status atual do orçamento.");
        }
        jdbc.sql("""
                        update treatment_plans set status = ?, updated_at = now(), version = version + 1,
                            notes = case when cast(? as text) is null then notes
                                else concat_ws(E'\\n', notes, cast(? as text)) end
                        where tenant_id = ? and id = ?
                        """)
                .params(to, reason, reason == null ? null : "Motivo (" + to + "): " + reason, tenantId, id)
                .update();
        audit.record(
                "CANCELLED".equals(to) ? AuditAction.RECORD_CANCELLED : AuditAction.RECORD_UPDATED,
                "treatment_plan",
                id,
                Map.of("status", to));
        return get(id);
    }

    private void validate(UUID tenantId, PlanRequest r) {
        refs.require("patients", tenantId, r.patientId(), "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        for (ItemRequest i : r.items()) {
            refs.require("procedures", tenantId, i.procedureId(), "Procedimento");
            BigDecimal gross = i.unitPrice().multiply(BigDecimal.valueOf(i.quantity()));
            if (zero(i.discount()).compareTo(gross) > 0) {
                throw ApiException.invalidField("items", "O desconto de um item não pode ser maior que o valor dele.");
            }
        }
    }

    private void insertItems(UUID tenantId, UUID planId, List<ItemRequest> items) {
        int order = 0;
        for (ItemRequest i : items) {
            jdbc.sql("""
                            insert into treatment_plan_items (id, tenant_id, plan_id, procedure_id, description, tooth,
                                surfaces, quantity, unit_price, discount, sort_order)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            Ids.newId(),
                            tenantId,
                            planId,
                            i.procedureId(),
                            i.description().strip(),
                            i.tooth(),
                            blank(i.surfaces()),
                            i.quantity(),
                            i.unitPrice(),
                            zero(i.discount()),
                            order++)
                    .update();
        }
    }

    private void ensureNonNegative(UUID planId) {
        PlanView plan = get(planId);
        BigDecimal beforePlanDiscount = plan.subtotal().subtract(plan.itemsDiscount());
        if (plan.discount().compareTo(beforePlanDiscount) > 0) {
            throw ApiException.invalidField(
                    "discount", "O desconto geral não pode ser maior que o valor do orçamento.");
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
