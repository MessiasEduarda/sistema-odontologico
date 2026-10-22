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
import br.com.odontosaas.finance.repository.CashFlowRepository;
import br.com.odontosaas.finance.repository.DayTotalRow;
import br.com.odontosaas.finance.repository.InstallmentLockRow;
import br.com.odontosaas.finance.repository.InstallmentRepository;
import br.com.odontosaas.finance.repository.PaymentLockRow;
import br.com.odontosaas.finance.repository.PaymentRepository;
import br.com.odontosaas.finance.repository.ReceivableHeadRow;
import br.com.odontosaas.finance.repository.ReceivableRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {

    private final ReceivableRepository receivableRepo;
    private final InstallmentRepository installmentRepo;
    private final PaymentRepository paymentRepo;
    private final CashFlowRepository cashFlowRepo;
    private final TenantRefs refs;
    private final AuditService audit;

    public FinanceService(
            ReceivableRepository receivableRepo,
            InstallmentRepository installmentRepo,
            PaymentRepository paymentRepo,
            CashFlowRepository cashFlowRepo,
            TenantRefs refs,
            AuditService audit) {
        this.receivableRepo = receivableRepo;
        this.installmentRepo = installmentRepo;
        this.paymentRepo = paymentRepo;
        this.cashFlowRepo = cashFlowRepo;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional
    public ReceivableView createReceivable(ReceivableRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, r.patientId(), "Paciente");
        UUID id = insertReceivable(
                tenantId, r.patientId(), null, r.description(), r.total(), r.installments(), r.firstDueDate());
        return receivable(id);
    }

    public void createReceivableForApprovedPlan(TreatmentPlanApproved e) {
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
        receivableRepo.insert(
                id,
                tenantId,
                patientId,
                planId,
                description.strip(),
                total,
                TenantContext.userId().orElse(null));
        List<BigDecimal> parts = split(total, installments);
        for (int n = 0; n < parts.size(); n++) {
            installmentRepo.insert(Ids.newId(), tenantId, id, n + 1, firstDue.plusMonths(n), parts.get(n));
        }
        audit.record(AuditAction.RECORD_CREATED, "receivable", id, Map.of("total", total.toPlainString()));
        return id;
    }

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
        return receivableRepo.list(tenantId, status, patientId, like);
    }

    @Transactional(readOnly = true)
    public ReceivableView receivable(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        ReceivableHeadRow h =
                receivableRepo.findHead(tenantId, id).orElseThrow(() -> ApiException.notFound("Conta a receber"));
        Map<UUID, List<PaymentView>> payments = paymentRepo.findByReceivable(tenantId, id);
        List<InstallmentView> installments = installmentRepo.findViews(tenantId, id, payments);
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
        receivableRepo.cancel(reason.strip(), tenantId, id);
        installmentRepo.cancelByReceivable(tenantId, id);
        audit.record(AuditAction.RECORD_CANCELLED, "receivable", id, Map.of());
        return receivable(id);
    }

    @Transactional(readOnly = true)
    public List<InstallmentRow> installments(LocalDate from, LocalDate to, String filter) {
        UUID tenantId = TenantContext.requireTenantId();
        return installmentRepo.list(tenantId, from, to, filter);
    }

    @Transactional
    public ReceivableView pay(UUID installmentId, PaymentRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        InstallmentLockRow inst = installmentRepo
                .findForUpdate(tenantId, installmentId)
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
        paymentRepo.insert(
                paymentId,
                tenantId,
                installmentId,
                r.amount(),
                r.method(),
                Timestamp.from(paidAt),
                blank(r.notes()),
                TenantContext.userId().orElse(null));
        applyToInstallment(tenantId, installmentId, inst.receivableId(), r.amount());
        audit.record(
                AuditAction.PAYMENT_RECEIVED,
                "payment",
                paymentId,
                Map.of("amount", r.amount().toPlainString(), "method", r.method()));
        return receivable(inst.receivableId());
    }

    @Transactional
    public ReceivableView refund(UUID paymentId, BigDecimal amount, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        PaymentLockRow p =
                paymentRepo.findForRefund(tenantId, paymentId).orElseThrow(() -> ApiException.notFound("Recebimento"));
        BigDecimal refundable = p.amount().subtract(p.refundedAmount());
        if (amount.compareTo(refundable) > 0) {
            throw ApiException.invalidField("amount", "O estorno é maior que o valor disponível (" + refundable + ").");
        }
        paymentRepo.insertRefund(
                Ids.newId(),
                tenantId,
                paymentId,
                amount,
                reason.strip(),
                TenantContext.userId().orElse(null));
        paymentRepo.addRefunded(amount, tenantId, paymentId);
        applyToInstallment(tenantId, p.installmentId(), p.receivableId(), amount.negate());
        audit.record(AuditAction.PAYMENT_REFUNDED, "payment", paymentId, Map.of("amount", amount.toPlainString()));
        return receivable(p.receivableId());
    }

    private void applyToInstallment(UUID tenantId, UUID installmentId, UUID receivableId, BigDecimal delta) {
        installmentRepo.applyDelta(delta, tenantId, installmentId);
        receivableRepo.refreshStatus(tenantId, receivableId);
    }

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
        for (DayTotalRow row : cashFlowRepo.paymentTotalsByDay(zone, tenantId, from, to)) {
            days.get(row.day())[0] = days.get(row.day())[0].add(row.total());
        }
        for (DayTotalRow row : cashFlowRepo.refundTotalsByDay(zone, tenantId, from, to)) {
            days.get(row.day())[1] = days.get(row.day())[1].add(row.total());
        }
        for (DayTotalRow row : cashFlowRepo.paidPayableTotalsByDay(zone, tenantId, from, to)) {
            days.get(row.day())[1] = days.get(row.day())[1].add(row.total());
        }
        List<CashFlowDay> list = new ArrayList<>();
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        for (var e : days.entrySet()) {
            BigDecimal[] v = e.getValue();
            list.add(new CashFlowDay(e.getKey(), v[0], v[1], v[0].subtract(v[1])));
            in = in.add(v[0]);
            out = out.add(v[1]);
        }
        List<MethodTotal> byMethod = cashFlowRepo.totalsByMethod(tenantId, zone, from, to);
        return new CashFlowView(from, to, in, out, in.subtract(out), list, byMethod);
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary() {
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        return cashFlowRepo.summary(tenantId, zone);
    }

    String zone(UUID tenantId) {
        return cashFlowRepo.timeZone(tenantId);
    }

    static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
