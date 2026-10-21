package br.com.odontosaas.finance.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.finance.dto.request.PayableRequest;
import br.com.odontosaas.finance.dto.request.SupplierRequest;
import br.com.odontosaas.finance.dto.response.PayableView;
import br.com.odontosaas.finance.dto.response.SupplierView;
import br.com.odontosaas.finance.repository.PayableRepository;
import br.com.odontosaas.finance.repository.SupplierRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Flags;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas a pagar e fornecedores. */
@Service
public class PayableService {

    private final PayableRepository payables;
    private final SupplierRepository suppliers;
    private final TenantRefs refs;
    private final AuditService audit;

    public PayableService(
            PayableRepository payables, SupplierRepository suppliers, TenantRefs refs, AuditService audit) {
        this.payables = payables;
        this.suppliers = suppliers;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PayableView> list(String status, LocalDate from, LocalDate to) {
        return payables.list(TenantContext.requireTenantId(), status, from, to);
    }

    private PayableView get(UUID id) {
        return payables.find(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> ApiException.notFound("Conta a pagar"));
    }

    @Transactional
    public PayableView create(PayableRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        UUID id = Ids.newId();
        payables.insert(
                id,
                tenantId,
                r.supplierId(),
                r.description().strip(),
                r.category().strip(),
                r.amount(),
                r.dueDate(),
                FinanceService.blank(r.notes()),
                TenantContext.userId().orElse(null));
        audit.record(AuditAction.RECORD_CREATED, "payable", id, Map.of());
        return get(id);
    }

    @Transactional
    public PayableView update(UUID id, PayableRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        requireOpen(get(id));
        payables.update(
                r.supplierId(),
                r.description().strip(),
                r.category().strip(),
                r.amount(),
                r.dueDate(),
                FinanceService.blank(r.notes()),
                tenantId,
                id);
        audit.record(AuditAction.RECORD_UPDATED, "payable", id, Map.of());
        return get(id);
    }

    @Transactional
    public PayableView pay(UUID id, String method, Instant paidAt) {
        requireOpen(get(id));
        payables.markPaid(
                Timestamp.from(paidAt == null ? Instant.now() : paidAt), method, TenantContext.requireTenantId(), id);
        audit.record(AuditAction.RECORD_UPDATED, "payable", id, Map.of("status", "PAID"));
        return get(id);
    }

    @Transactional
    public PayableView cancel(UUID id, String reason) {
        requireOpen(get(id));
        payables.cancel("Cancelada: " + reason.strip(), TenantContext.requireTenantId(), id);
        audit.record(AuditAction.RECORD_CANCELLED, "payable", id, Map.of());
        return get(id);
    }

    private static void requireOpen(PayableView p) {
        if (!"OPEN".equals(p.status())) {
            throw ApiException.businessRule("PAYABLE_NOT_OPEN", "Esta conta não está em aberto.");
        }
    }

    // ------------------------------------------------------------------ Fornecedores

    @Transactional(readOnly = true)
    public List<SupplierView> suppliers() {
        return suppliers.list(TenantContext.requireTenantId());
    }

    @Transactional
    public SupplierView createSupplier(SupplierRequest r) {
        UUID id = Ids.newId();
        suppliers.insert(
                id,
                TenantContext.requireTenantId(),
                r.name().strip(),
                FinanceService.blank(r.document()),
                FinanceService.blank(r.phone()),
                FinanceService.blank(r.email()),
                FinanceService.blank(r.notes()),
                Flags.activeIfOmitted(r.active()));
        audit.record(AuditAction.RECORD_CREATED, "supplier", id, Map.of());
        return new SupplierView(
                id, r.name().strip(), r.document(), r.phone(), r.email(), r.notes(), Flags.activeIfOmitted(r.active()));
    }

    @Transactional
    public SupplierView updateSupplier(UUID id, SupplierRequest r) {
        int updated = suppliers.update(
                r.name().strip(),
                FinanceService.blank(r.document()),
                FinanceService.blank(r.phone()),
                FinanceService.blank(r.email()),
                FinanceService.blank(r.notes()),
                r.active(),
                TenantContext.requireTenantId(),
                id);
        Versioned.requireFound(updated, "Fornecedor");
        audit.record(AuditAction.RECORD_UPDATED, "supplier", id, Map.of());
        boolean active = suppliers.isActive(TenantContext.requireTenantId(), id);
        return new SupplierView(id, r.name().strip(), r.document(), r.phone(), r.email(), r.notes(), active);
    }
}
