package br.com.odontosaas.finance.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.finance.dto.request.PaymentRequest;
import br.com.odontosaas.finance.dto.request.ReceivableRequest;
import br.com.odontosaas.finance.dto.response.CashFlowDay;
import br.com.odontosaas.finance.dto.response.CashFlowView;
import br.com.odontosaas.finance.dto.response.FinanceSummary;
import br.com.odontosaas.finance.dto.response.InstallmentRow;
import br.com.odontosaas.finance.dto.response.InstallmentView;
import br.com.odontosaas.finance.dto.response.MethodTotal;
import br.com.odontosaas.finance.dto.response.PaymentView;
import br.com.odontosaas.finance.dto.response.ReceivableSummary;
import br.com.odontosaas.finance.dto.response.ReceivableView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import br.com.odontosaas.treatment.event.TreatmentPlanApproved;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas a receber, parcelas, recebimentos, estornos e fluxo de caixa. Valores em BigDecimal; nada é apagado
 * (cancelamento/estorno ficam registrados). Datas "do dia" seguem o fuso da clínica.
 */
@Service
public class FinanceService {

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;

    public FinanceService(JdbcClient jdbc, TenantRefs refs, AuditService audit) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ Contas a receber

    @Transactional
    public ReceivableView createReceivable(ReceivableRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, r.patientId(), "Paciente");
        UUID id = insertReceivable(
                tenantId, r.patientId(), null, r.description(), r.total(), r.installments(), r.firstDueDate());
        return receivable(id);
    }

    /** Orçamento aprovado com geração de cobrança: cria a conta a receber parcelada. */
    @EventListener
    public void onTreatmentApproved(TreatmentPlanApproved e) {
        insertReceivable(
                e.tenantId(),
                e.patientId(),
                e.planId(),
                e.description(),
                e.total(),
                e.installments(),
                e.firstDueDate());
    }

    private UUID insertReceivable(
            UUID tenantId,
            UUID patientId,
            UUID planId,
            String description,
            BigDecimal total,
            int installments,
            LocalDate firstDue) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into receivables (id, tenant_id, patient_id, treatment_plan_id, description, total,
                            created_by) values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        planId,
                        description.strip(),
                        total,
                        TenantContext.userId().orElse(null))
                .update();
        List<BigDecimal> parts = split(total, installments);
        for (int n = 0; n < parts.size(); n++) {
            jdbc.sql("""
                            insert into installments (id, tenant_id, receivable_id, number, due_date, amount)
                            values (?, ?, ?, ?, ?, ?)
                            """)
                    .params(Ids.newId(), tenantId, id, n + 1, firstDue.plusMonths(n), parts.get(n))
                    .update();
        }
        audit.record(AuditAction.RECORD_CREATED, "receivable", id, Map.of("total", total.toPlainString()));
        return id;
    }

    /** Divide o total em N parcelas; os centavos que sobram vão para a primeira. */
    public static List<BigDecimal> split(BigDecimal total, int n) {
        BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(BigDecimal.valueOf(n)));
        List<BigDecimal> parts = new ArrayList<>();
        for (int i = 0; i < n; i++) parts.add(i == 0 ? base.add(remainder) : base);
        return parts;
    }

    @Transactional(readOnly = true)
    public List<ReceivableSummary> receivables(String status, UUID patientId, String query) {
        UUID tenantId = TenantContext.requireTenantId();
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbc.sql("""
                        select r.id, r.patient_id, p.full_name as patient_name, r.treatment_plan_id, r.description,
                               r.total, coalesce(sum(i.paid_amount), 0) as paid,
                               case when r.status = 'CANCELLED' then 0
                                    else r.total - coalesce(sum(i.paid_amount), 0) end as balance,
                               r.status,
                               min(i.due_date) filter (where i.status = 'OPEN') as next_due_date,
                               count(*) filter (where i.status = 'OPEN' and i.due_date < current_date)
                                   as overdue_installments,
                               r.created_at
                        from receivables r
                        join patients p on p.tenant_id = r.tenant_id and p.id = r.patient_id
                        left join installments i on i.tenant_id = r.tenant_id and i.receivable_id = r.id
                        where r.tenant_id = ?
                          and (cast(? as text) is null or r.status = ?)
                          and (cast(? as uuid) is null or r.patient_id = ?)
                          and (cast(? as text) is null or lower(p.full_name) like ? or lower(r.description) like ?)
                        group by r.id, p.full_name order by r.created_at desc limit 500
                        """)
                .params(tenantId, status, status, patientId, patientId, like, like, like)
                .query(ReceivableSummary.class)
                .list();
    }

    @Transactional(readOnly = true)
    public ReceivableView receivable(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        record Head(
                UUID id,
                UUID patientId,
                String patientName,
                UUID treatmentPlanId,
                String description,
                BigDecimal total,
                String status,
                String cancelReason,
                Instant createdAt) {}
        Head h = jdbc.sql("""
                        select r.id, r.patient_id, p.full_name as patient_name, r.treatment_plan_id, r.description,
                               r.total, r.status, r.cancel_reason, r.created_at
                        from receivables r join patients p on p.tenant_id = r.tenant_id and p.id = r.patient_id
                        where r.tenant_id = ? and r.id = ?
                        """)
                .params(tenantId, id)
                .query(Head.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Conta a receber"));
        Map<UUID, List<PaymentView>> payments = new LinkedHashMap<>();
        jdbc.sql("""
                        select pa.installment_id, pa.id, pa.amount, pa.refunded_amount, pa.method, pa.paid_at,
                               pa.notes, u.name as created_by_name
                        from payments pa
                        join installments i on i.tenant_id = pa.tenant_id and i.id = pa.installment_id
                        left join users u on u.id = pa.created_by
                        where pa.tenant_id = ? and i.receivable_id = ? order by pa.paid_at
                        """)
                .params(tenantId, id)
                .query((rs, n) -> {
                    payments.computeIfAbsent(rs.getObject("installment_id", UUID.class), k -> new ArrayList<>())
                            .add(new PaymentView(
                                    rs.getObject("id", UUID.class),
                                    rs.getBigDecimal("amount"),
                                    rs.getBigDecimal("refunded_amount"),
                                    rs.getString("method"),
                                    rs.getTimestamp("paid_at").toInstant(),
                                    rs.getString("notes"),
                                    rs.getString("created_by_name")));
                    return null;
                })
                .list();
        List<InstallmentView> installments = jdbc.sql("""
                        select id, number, due_date, amount, paid_amount, amount - paid_amount as balance, status,
                               (status = 'OPEN' and due_date < current_date) as overdue
                        from installments where tenant_id = ? and receivable_id = ? order by number
                        """)
                .params(tenantId, id)
                .query((rs, n) -> new InstallmentView(
                        rs.getObject("id", UUID.class),
                        rs.getInt("number"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("paid_amount"),
                        rs.getBigDecimal("balance"),
                        rs.getString("status"),
                        rs.getBoolean("overdue"),
                        payments.getOrDefault(rs.getObject("id", UUID.class), List.of())))
                .list();
        BigDecimal paid =
                installments.stream().map(InstallmentView::paidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance =
                "CANCELLED".equals(h.status()) ? BigDecimal.ZERO : h.total().subtract(paid);
        return new ReceivableView(
                h.id(),
                h.patientId(),
                h.patientName(),
                h.treatmentPlanId(),
                h.description(),
                h.total(),
                paid,
                balance,
                h.status(),
                h.cancelReason(),
                h.createdAt(),
                installments);
    }

    @Transactional
    public ReceivableView cancelReceivable(UUID id, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        ReceivableView r = receivable(id);
        if (!"OPEN".equals(r.status())) {
            throw ApiException.businessRule("RECEIVABLE_NOT_OPEN", "Só é possível cancelar contas em aberto.");
        }
        if (r.paid().signum() > 0) {
            throw ApiException.businessRule(
                    "RECEIVABLE_HAS_PAYMENTS", "Estorne os recebimentos antes de cancelar esta conta.");
        }
        jdbc.sql("""
                        update receivables set status = 'CANCELLED', cancel_reason = ?, version = version + 1
                        where tenant_id = ? and id = ?
                        """).params(reason.strip(), tenantId, id).update();
        jdbc.sql("update installments set status = 'CANCELLED' where tenant_id = ? and receivable_id = ?")
                .params(tenantId, id)
                .update();
        audit.record(AuditAction.RECORD_CANCELLED, "receivable", id, Map.of());
        return receivable(id);
    }

    @Transactional(readOnly = true)
    public List<InstallmentRow> installments(LocalDate from, LocalDate to, String filter) {
        UUID tenantId = TenantContext.requireTenantId();
        String where = switch (filter == null ? "OPEN" : filter) {
            case "OVERDUE" -> " and i.status = 'OPEN' and i.due_date < current_date";
            case "PAID" -> " and i.status = 'PAID'";
            case "ALL" -> "";
            default -> " and i.status = 'OPEN'";
        };
        return jdbc.sql("""
                        select i.id, i.receivable_id, r.patient_id, p.full_name as patient_name, r.description,
                               i.number,
                               (select count(*) from installments x where x.tenant_id = i.tenant_id
                                   and x.receivable_id = i.receivable_id) as installments_count,
                               i.due_date, i.amount, i.paid_amount, i.amount - i.paid_amount as balance, i.status,
                               (i.status = 'OPEN' and i.due_date < current_date) as overdue
                        from installments i
                        join receivables r on r.tenant_id = i.tenant_id and r.id = i.receivable_id
                        join patients p on p.tenant_id = r.tenant_id and p.id = r.patient_id
                        where i.tenant_id = ? and (cast(? as date) is null or i.due_date >= ?)
                          and (cast(? as date) is null or i.due_date <= ?)
                        """ + where + " order by i.due_date, p.full_name limit 1000")
                .params(tenantId, from, from, to, to)
                .query(InstallmentRow.class)
                .list();
    }

    /** Recebimento (total ou parcial) de uma parcela. Não permite receber mais que o saldo. */
    @Transactional
    public ReceivableView pay(UUID installmentId, PaymentRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        record Inst(UUID receivableId, BigDecimal amount, BigDecimal paidAmount, String status) {}
        Inst inst = jdbc.sql("""
                        select receivable_id, amount, paid_amount, status from installments
                        where tenant_id = ? and id = ? for update
                        """)
                .params(tenantId, installmentId)
                .query(Inst.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Parcela"));
        if (!"OPEN".equals(inst.status())) {
            throw ApiException.businessRule("INSTALLMENT_NOT_OPEN", "Esta parcela não está em aberto.");
        }
        BigDecimal remaining = inst.amount().subtract(inst.paidAmount());
        if (r.amount().compareTo(remaining) > 0) {
            throw ApiException.invalidField("amount", "O valor é maior que o saldo da parcela (" + remaining + ").");
        }
        Instant paidAt = r.paidAt() == null ? Instant.now() : r.paidAt();
        if (paidAt.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            throw ApiException.invalidField("paidAt", "A data do recebimento não pode estar no futuro.");
        }
        UUID paymentId = Ids.newId();
        jdbc.sql("""
                        insert into payments (id, tenant_id, installment_id, amount, method, paid_at, notes, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        paymentId,
                        tenantId,
                        installmentId,
                        r.amount(),
                        r.method(),
                        Timestamp.from(paidAt),
                        blank(r.notes()),
                        TenantContext.userId().orElse(null))
                .update();
        applyToInstallment(tenantId, installmentId, inst.receivableId(), r.amount());
        audit.record(
                AuditAction.PAYMENT_RECEIVED,
                "payment",
                paymentId,
                Map.of("amount", r.amount().toPlainString(), "method", r.method()));
        return receivable(inst.receivableId());
    }

    /** Estorno total/parcial de um recebimento: o pagamento original é mantido e o estorno fica registrado. */
    @Transactional
    public ReceivableView refund(UUID paymentId, BigDecimal amount, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        record Pay(UUID installmentId, BigDecimal amount, BigDecimal refundedAmount, UUID receivableId) {}
        Pay p = jdbc.sql("""
                        select pa.installment_id, pa.amount, pa.refunded_amount, i.receivable_id
                        from payments pa join installments i on i.tenant_id = pa.tenant_id and i.id = pa.installment_id
                        where pa.tenant_id = ? and pa.id = ? for update of pa
                        """)
                .params(tenantId, paymentId)
                .query(Pay.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Recebimento"));
        BigDecimal refundable = p.amount().subtract(p.refundedAmount());
        if (amount.compareTo(refundable) > 0) {
            throw ApiException.invalidField("amount", "O estorno é maior que o valor disponível (" + refundable + ").");
        }
        jdbc.sql("""
                        insert into refunds (id, tenant_id, payment_id, amount, reason, created_by)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        Ids.newId(),
                        tenantId,
                        paymentId,
                        amount,
                        reason.strip(),
                        TenantContext.userId().orElse(null))
                .update();
        jdbc.sql("update payments set refunded_amount = refunded_amount + ? where tenant_id = ? and id = ?")
                .params(amount, tenantId, paymentId)
                .update();
        applyToInstallment(tenantId, p.installmentId(), p.receivableId(), amount.negate());
        audit.record(AuditAction.PAYMENT_REFUNDED, "payment", paymentId, Map.of("amount", amount.toPlainString()));
        return receivable(p.receivableId());
    }

    private void applyToInstallment(UUID tenantId, UUID installmentId, UUID receivableId, BigDecimal delta) {
        jdbc.sql("""
                        update installments set paid_amount = paid_amount + ?,
                            status = case when paid_amount + ? >= amount then 'PAID' else 'OPEN' end,
                            version = version + 1
                        where tenant_id = ? and id = ?
                        """).params(delta, delta, tenantId, installmentId).update();
        jdbc.sql("""
                        update receivables r set status = case
                                when not exists (select 1 from installments i where i.tenant_id = r.tenant_id
                                    and i.receivable_id = r.id and i.status = 'OPEN') then 'PAID' else 'OPEN' end,
                            version = version + 1
                        where r.tenant_id = ? and r.id = ? and r.status <> 'CANCELLED'
                        """).params(tenantId, receivableId).update();
    }

    // ------------------------------------------------------------------ Fluxo de caixa e resumo

    @Transactional(readOnly = true)
    public CashFlowView cashFlow(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Período inválido (máximo de 400 dias).");
        }
        String zone = zone(tenantId);
        Map<LocalDate, BigDecimal[]> days = new java.util.TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        }
        jdbc.sql("""
                        select (paid_at at time zone ?)::date as day, sum(amount) as total from payments
                        where tenant_id = ? and (paid_at at time zone ?)::date between ? and ? group by 1
                        """)
                .params(zone, tenantId, zone, from, to)
                .query((rs, n) -> days.get(rs.getObject("day", LocalDate.class))[0] =
                        days.get(rs.getObject("day", LocalDate.class))[0].add(rs.getBigDecimal("total")))
                .list();
        jdbc.sql("""
                        select (refunded_at at time zone ?)::date as day, sum(amount) as total from refunds
                        where tenant_id = ? and (refunded_at at time zone ?)::date between ? and ? group by 1
                        """)
                .params(zone, tenantId, zone, from, to)
                .query((rs, n) -> days.get(rs.getObject("day", LocalDate.class))[1] =
                        days.get(rs.getObject("day", LocalDate.class))[1].add(rs.getBigDecimal("total")))
                .list();
        jdbc.sql("""
                        select (paid_at at time zone ?)::date as day, sum(amount) as total from payables
                        where tenant_id = ? and status = 'PAID' and (paid_at at time zone ?)::date between ? and ?
                        group by 1
                        """)
                .params(zone, tenantId, zone, from, to)
                .query((rs, n) -> days.get(rs.getObject("day", LocalDate.class))[1] =
                        days.get(rs.getObject("day", LocalDate.class))[1].add(rs.getBigDecimal("total")))
                .list();
        List<CashFlowDay> list = new ArrayList<>();
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        for (var e : days.entrySet()) {
            BigDecimal[] v = e.getValue();
            list.add(new CashFlowDay(e.getKey(), v[0], v[1], v[0].subtract(v[1])));
            in = in.add(v[0]);
            out = out.add(v[1]);
        }
        List<MethodTotal> byMethod = jdbc.sql("""
                        select method, sum(amount - refunded_amount) as amount from payments
                        where tenant_id = ? and (paid_at at time zone ?)::date between ? and ?
                        group by method order by 2 desc
                        """)
                .params(tenantId, zone, from, to)
                .query(MethodTotal.class)
                .list();
        return new CashFlowView(from, to, in, out, in.subtract(out), list, byMethod);
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary() {
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        return jdbc.sql("""
                        select
                          (select coalesce(sum(amount), 0) from payments where tenant_id = ?
                             and date_trunc('month', paid_at at time zone ?) = date_trunc('month', now() at time zone ?))
                          - (select coalesce(sum(amount), 0) from refunds where tenant_id = ?
                             and date_trunc('month', refunded_at at time zone ?)
                                 = date_trunc('month', now() at time zone ?)) as received_this_month,
                          (select coalesce(sum(amount), 0) from payables where tenant_id = ? and status = 'PAID'
                             and date_trunc('month', paid_at at time zone ?) = date_trunc('month', now() at time zone ?))
                             as paid_out_this_month,
                          (select coalesce(sum(amount - paid_amount), 0) from installments where tenant_id = ?
                             and status = 'OPEN') as open_receivables,
                          (select coalesce(sum(amount - paid_amount), 0) from installments where tenant_id = ?
                             and status = 'OPEN' and due_date < current_date) as overdue_receivables,
                          (select count(*) from installments where tenant_id = ? and status = 'OPEN'
                             and due_date < current_date) as overdue_installments,
                          (select coalesce(sum(amount), 0) from payables where tenant_id = ? and status = 'OPEN')
                             as open_payables,
                          (select coalesce(sum(amount), 0) from payables where tenant_id = ? and status = 'OPEN'
                             and due_date <= current_date + 7) as payables_due_next7_days
                        """)
                .params(
                        tenantId, zone, zone, tenantId, zone, zone, tenantId, zone, zone, tenantId, tenantId, tenantId,
                        tenantId, tenantId)
                .query(FinanceSummary.class)
                .single();
    }

    String zone(UUID tenantId) {
        return jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
