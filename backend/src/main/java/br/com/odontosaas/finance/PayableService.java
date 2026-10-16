package br.com.odontosaas.finance;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.finance.FinanceDtos.PayableRequest;
import br.com.odontosaas.finance.FinanceDtos.PayableView;
import br.com.odontosaas.finance.FinanceDtos.SupplierRequest;
import br.com.odontosaas.finance.FinanceDtos.SupplierView;
import br.com.odontosaas.shared.ApiException;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas a pagar e fornecedores. */
@Service
public class PayableService {

    private static final String SELECT = """
            select b.id, b.supplier_id, s.name as supplier_name, b.description, b.category, b.amount, b.due_date,
                   b.status, (b.status = 'OPEN' and b.due_date < current_date) as overdue, b.paid_at,
                   b.payment_method, b.notes
            from payables b left join suppliers s on s.tenant_id = b.tenant_id and s.id = b.supplier_id
            """;

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;

    public PayableService(JdbcClient jdbc, TenantRefs refs, AuditService audit) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PayableView> list(String status, LocalDate from, LocalDate to) {
        return jdbc.sql(SELECT + """
                        where b.tenant_id = ? and (cast(? as text) is null or b.status = ?)
                          and (cast(? as date) is null or b.due_date >= ?) and (cast(? as date) is null or b.due_date <= ?)
                        order by b.due_date limit 1000
                        """)
                .params(TenantContext.requireTenantId(), status, status, from, from, to, to)
                .query(PayableView.class)
                .list();
    }

    private PayableView get(UUID id) {
        return jdbc.sql(SELECT + " where b.tenant_id = ? and b.id = ?")
                .params(TenantContext.requireTenantId(), id)
                .query(PayableView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Conta a pagar"));
    }

    @Transactional
    public PayableView create(PayableRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into payables (id, tenant_id, supplier_id, description, category, amount, due_date, notes,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.supplierId(),
                        r.description().strip(),
                        r.category().strip(),
                        r.amount(),
                        r.dueDate(),
                        FinanceService.blank(r.notes()),
                        TenantContext.userId().orElse(null))
                .update();
        audit.record(AuditAction.RECORD_CREATED, "payable", id, Map.of());
        return get(id);
    }

    @Transactional
    public PayableView update(UUID id, PayableRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        requireOpen(get(id));
        jdbc.sql("""
                        update payables set supplier_id = ?, description = ?, category = ?, amount = ?, due_date = ?,
                            notes = ?, version = version + 1 where tenant_id = ? and id = ? and status = 'OPEN'
                        """)
                .params(
                        r.supplierId(),
                        r.description().strip(),
                        r.category().strip(),
                        r.amount(),
                        r.dueDate(),
                        FinanceService.blank(r.notes()),
                        tenantId,
                        id)
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "payable", id, Map.of());
        return get(id);
    }

    @Transactional
    public PayableView pay(UUID id, String method, Instant paidAt) {
        requireOpen(get(id));
        jdbc.sql("""
                        update payables set status = 'PAID', paid_at = ?, payment_method = ?, version = version + 1
                        where tenant_id = ? and id = ? and status = 'OPEN'
                        """)
                .params(
                        Timestamp.from(paidAt == null ? Instant.now() : paidAt),
                        method,
                        TenantContext.requireTenantId(),
                        id)
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "payable", id, Map.of("status", "PAID"));
        return get(id);
    }

    @Transactional
    public PayableView cancel(UUID id, String reason) {
        requireOpen(get(id));
        jdbc.sql("""
                        update payables set status = 'CANCELLED', notes = concat_ws(E'\\n', notes, ?),
                            version = version + 1 where tenant_id = ? and id = ? and status = 'OPEN'
                        """)
                .params("Cancelada: " + reason.strip(), TenantContext.requireTenantId(), id)
                .update();
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
        return jdbc.sql("select id, name, document, phone, email, notes, active from suppliers where tenant_id = ?"
                        + " order by name")
                .param(TenantContext.requireTenantId())
                .query(SupplierView.class)
                .list();
    }

    @Transactional
    public SupplierView createSupplier(SupplierRequest r) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into suppliers (id, tenant_id, name, document, phone, email, notes, active)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        TenantContext.requireTenantId(),
                        r.name().strip(),
                        FinanceService.blank(r.document()),
                        FinanceService.blank(r.phone()),
                        FinanceService.blank(r.email()),
                        FinanceService.blank(r.notes()),
                        r.active())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "supplier", id, Map.of());
        return new SupplierView(id, r.name().strip(), r.document(), r.phone(), r.email(), r.notes(), r.active());
    }

    @Transactional
    public SupplierView updateSupplier(UUID id, SupplierRequest r) {
        int updated = jdbc.sql("""
                        update suppliers set name = ?, document = ?, phone = ?, email = ?, notes = ?, active = ?
                        where tenant_id = ? and id = ?
                        """)
                .params(
                        r.name().strip(),
                        FinanceService.blank(r.document()),
                        FinanceService.blank(r.phone()),
                        FinanceService.blank(r.email()),
                        FinanceService.blank(r.notes()),
                        r.active(),
                        TenantContext.requireTenantId(),
                        id)
                .update();
        Versioned.requireFound(updated, "Fornecedor");
        audit.record(AuditAction.RECORD_UPDATED, "supplier", id, Map.of());
        return new SupplierView(id, r.name().strip(), r.document(), r.phone(), r.email(), r.notes(), r.active());
    }
}
