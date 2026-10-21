package br.com.odontosaas.finance.repository;

import br.com.odontosaas.finance.dto.response.PayableView;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados das contas a pagar (tabela payables). Participa da transação do service. */
@Repository
public class PayableRepository {

    private static final String SELECT = """
            select b.id, b.supplier_id, s.name as supplier_name, b.description, b.category, b.amount, b.due_date,
                   b.status, (b.status = 'OPEN' and b.due_date < current_date) as overdue, b.paid_at,
                   b.payment_method, b.notes
            from payables b left join suppliers s on s.tenant_id = b.tenant_id and s.id = b.supplier_id
            """;

    private final JdbcClient jdbc;

    public PayableRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PayableView> list(UUID tenantId, String status, LocalDate from, LocalDate to) {
        return jdbc.sql(SELECT + """
                        where b.tenant_id = ? and (cast(? as text) is null or b.status = ?)
                          and (cast(? as date) is null or b.due_date >= ?) and (cast(? as date) is null or b.due_date <= ?)
                        order by b.due_date limit 1000
                        """)
                .params(tenantId, status, status, from, from, to, to)
                .query(PayableView.class)
                .list();
    }

    public Optional<PayableView> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where b.tenant_id = ? and b.id = ?")
                .params(tenantId, id)
                .query(PayableView.class)
                .optional();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID supplierId,
            String description,
            String category,
            BigDecimal amount,
            LocalDate dueDate,
            String notes,
            UUID createdBy) {
        jdbc.sql("""
                        insert into payables (id, tenant_id, supplier_id, description, category, amount, due_date, notes,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, supplierId, description, category, amount, dueDate, notes, createdBy)
                .update();
    }

    public void update(
            UUID supplierId,
            String description,
            String category,
            BigDecimal amount,
            LocalDate dueDate,
            String notes,
            UUID tenantId,
            UUID id) {
        jdbc.sql("""
                        update payables set supplier_id = ?, description = ?, category = ?, amount = ?, due_date = ?,
                            notes = ?, version = version + 1 where tenant_id = ? and id = ? and status = 'OPEN'
                        """)
                .params(supplierId, description, category, amount, dueDate, notes, tenantId, id)
                .update();
    }

    public void markPaid(Timestamp paidAt, String method, UUID tenantId, UUID id) {
        jdbc.sql("""
                        update payables set status = 'PAID', paid_at = ?, payment_method = ?, version = version + 1
                        where tenant_id = ? and id = ? and status = 'OPEN'
                        """).params(paidAt, method, tenantId, id).update();
    }

    public void cancel(String note, UUID tenantId, UUID id) {
        jdbc.sql("""
                        update payables set status = 'CANCELLED', notes = concat_ws(E'\\n', notes, ?),
                            version = version + 1 where tenant_id = ? and id = ? and status = 'OPEN'
                        """).params(note, tenantId, id).update();
    }
}
