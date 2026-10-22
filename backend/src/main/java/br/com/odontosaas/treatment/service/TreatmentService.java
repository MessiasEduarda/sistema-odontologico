package br.com.odontosaas.treatment.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.medicalrecord.event.ProcedurePerformed;
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
import br.com.odontosaas.treatment.repository.TreatmentPlanHeadRow;
import br.com.odontosaas.treatment.repository.TreatmentPlanItemRepository;
import br.com.odontosaas.treatment.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TreatmentService {

    private static final Set<String> EDITABLE = Set.of("DRAFT", "PROPOSED");

    private final TreatmentPlanRepository plans;
    private final TreatmentPlanItemRepository planItems;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public TreatmentService(
            TreatmentPlanRepository plans,
            TreatmentPlanItemRepository planItems,
            TenantRefs refs,
            AuditService audit,
            ApplicationEventPublisher events) {
        this.plans = plans;
        this.planItems = planItems;
        this.refs = refs;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list(UUID patientId, String status) {
        UUID tenantId = TenantContext.requireTenantId();
        return plans.list(tenantId, patientId, blank(status));
    }

    @Transactional(readOnly = true)
    public PlanView get(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        TreatmentPlanHeadRow h =
                plans.findHead(tenantId, id).orElseThrow(() -> ApiException.notFound("Plano de tratamento"));
        List<ItemView> items = planItems.findByPlan(tenantId, id);
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
        plans.insert(
                id,
                tenantId,
                r.patientId(),
                r.professionalId(),
                r.title().strip(),
                zero(r.discount()),
                blank(r.paymentConditions()),
                blank(r.notes()),
                r.validUntil(),
                TenantContext.userId().orElse(null));
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
        int updated = plans.update(
                r.professionalId(),
                r.title().strip(),
                zero(r.discount()),
                blank(r.paymentConditions()),
                blank(r.notes()),
                r.validUntil(),
                tenantId,
                id,
                r.version());
        Versioned.check(updated, true, "Plano de tratamento");
        planItems.deleteByPlan(tenantId, id);
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
        int updated = plans.approve(r.approvedByName().strip(), tenantId, id, plan.version());
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

    public void applyProcedurePerformed(ProcedurePerformed event) {
        planItems.updateStatus(
                event.voided() ? "PLANNED" : "DONE", event.voided(), event.tenantId(), event.treatmentItemId());
        UUID planId = planItems.findPlanId(event.tenantId(), event.treatmentItemId());
        // Plano aprovado com todos os itens realizados (ou cancelados) passa a "concluído", e volta se reaberto.
        plans.refreshCompletion(event.tenantId(), planId);
    }

    private PlanView transition(UUID id, Set<String> from, String to, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        PlanView plan = get(id);
        if (!from.contains(plan.status())) {
            throw ApiException.businessRule("INVALID_PLAN_STATUS", "Ação não permitida no status atual do orçamento.");
        }
        plans.transition(to, reason, reason == null ? null : "Motivo (" + to + "): " + reason, tenantId, id);
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
            planItems.insert(
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
                    order++);
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
