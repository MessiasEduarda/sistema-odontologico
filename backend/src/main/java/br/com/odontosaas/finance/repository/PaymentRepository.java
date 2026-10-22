package br.com.odontosaas.finance.repository;

import br.com.odontosaas.finance.dto.response.PaymentView;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, List<PaymentView>> findByReceivable(UUID tenantId, UUID receivableId) {
        Map<UUID, List<PaymentView>> payments = new LinkedHashMap<>();
        jdbc.sql("""
                        select pa.installment_id, pa.id, pa.amount, pa.refunded_amount, pa.method, pa.paid_at,
                               pa.notes, u.name as created_by_name
                        from payments pa
                        join installments i on i.tenant_id = pa.tenant_id and i.id = pa.installment_id
                        left join users u on u.id = pa.created_by
                        where pa.tenant_id = ? and i.receivable_id = ? order by pa.paid_at
                        """)
                .params(tenantId, receivableId)
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
        return payments;
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID installmentId,
            BigDecimal amount,
            String method,
            Timestamp paidAt,
            String notes,
            UUID createdBy) {
        jdbc.sql("""
                        insert into payments (id, tenant_id, installment_id, amount, method, paid_at, notes, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, installmentId, amount, method, paidAt, notes, createdBy)
                .update();
    }

    public Optional<PaymentLockRow> findForRefund(UUID tenantId, UUID paymentId) {
        return jdbc.sql("""
                        select pa.installment_id, pa.amount, pa.refunded_amount, i.receivable_id
                        from payments pa join installments i on i.tenant_id = pa.tenant_id and i.id = pa.installment_id
                        where pa.tenant_id = ? and pa.id = ? for update of pa
                        """)
                .params(tenantId, paymentId)
                .query(PaymentLockRow.class)
                .optional();
    }

    public void insertRefund(UUID id, UUID tenantId, UUID paymentId, BigDecimal amount, String reason, UUID createdBy) {
        jdbc.sql("""
                        insert into refunds (id, tenant_id, payment_id, amount, reason, created_by)
                        values (?, ?, ?, ?, ?, ?)
                        """).params(id, tenantId, paymentId, amount, reason, createdBy).update();
    }

    public void addRefunded(BigDecimal amount, UUID tenantId, UUID paymentId) {
        jdbc.sql("update payments set refunded_amount = refunded_amount + ? where tenant_id = ? and id = ?")
                .params(amount, tenantId, paymentId)
                .update();
    }
}
