package br.com.odontosaas.finance.repository;

import br.com.odontosaas.finance.dto.response.InstallmentRow;
import br.com.odontosaas.finance.dto.response.InstallmentView;
import br.com.odontosaas.finance.dto.response.PaymentView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados das parcelas (tabela installments). Participa da transação do service. */
@Repository
public class InstallmentRepository {

    private final JdbcClient jdbc;

    public InstallmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID receivableId, int number, LocalDate dueDate, BigDecimal amount) {
        jdbc.sql("""
                        insert into installments (id, tenant_id, receivable_id, number, due_date, amount)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, receivableId, number, dueDate, amount)
                .update();
    }

    /** Parcelas de uma conta, cada uma com seus recebimentos (indexados pelo id da parcela). */
    public List<InstallmentView> findViews(
            UUID tenantId, UUID receivableId, Map<UUID, List<PaymentView>> paymentsByInstallment) {
        return jdbc.sql("""
                        select id, number, due_date, amount, paid_amount, amount - paid_amount as balance, status,
                               (status = 'OPEN' and due_date < current_date) as overdue
                        from installments where tenant_id = ? and receivable_id = ? order by number
                        """)
                .params(tenantId, receivableId)
                .query((rs, n) -> new InstallmentView(
                        rs.getObject("id", UUID.class),
                        rs.getInt("number"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("paid_amount"),
                        rs.getBigDecimal("balance"),
                        rs.getString("status"),
                        rs.getBoolean("overdue"),
                        paymentsByInstallment.getOrDefault(rs.getObject("id", UUID.class), List.of())))
                .list();
    }

    public void cancelByReceivable(UUID tenantId, UUID receivableId) {
        jdbc.sql("update installments set status = 'CANCELLED' where tenant_id = ? and receivable_id = ?")
                .params(tenantId, receivableId)
                .update();
    }

    /** Lista parcelas por período e filtro (OPEN, OVERDUE, PAID ou ALL; nulo equivale a OPEN). */
    public List<InstallmentRow> list(UUID tenantId, LocalDate from, LocalDate to, String filter) {
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

    /** Trava a parcela (for update) para receber sem corrida. */
    public Optional<InstallmentLockRow> findForUpdate(UUID tenantId, UUID installmentId) {
        return jdbc.sql("""
                        select receivable_id, amount, paid_amount, status from installments
                        where tenant_id = ? and id = ? for update
                        """)
                .params(tenantId, installmentId)
                .query(InstallmentLockRow.class)
                .optional();
    }

    /** Soma (ou subtrai, se negativo) um valor ao pago da parcela e recalcula o status. */
    public void applyDelta(BigDecimal delta, UUID tenantId, UUID installmentId) {
        jdbc.sql("""
                        update installments set paid_amount = paid_amount + ?,
                            status = case when paid_amount + ? >= amount then 'PAID' else 'OPEN' end,
                            version = version + 1
                        where tenant_id = ? and id = ?
                        """).params(delta, delta, tenantId, installmentId).update();
    }
}
