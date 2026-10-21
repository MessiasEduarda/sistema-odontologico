package br.com.odontosaas.finance.repository;

import br.com.odontosaas.finance.dto.response.ReceivableSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados das contas a receber (tabela receivables). Participa da transação do service. */
@Repository
public class ReceivableRepository {

    private final JdbcClient jdbc;

    public ReceivableRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID id, UUID tenantId, UUID patientId, UUID planId, String description, BigDecimal total, UUID createdBy) {
        jdbc.sql("""
                        insert into receivables (id, tenant_id, patient_id, treatment_plan_id, description, total,
                            created_by) values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, patientId, planId, description, total, createdBy)
                .update();
    }

    public List<ReceivableSummary> list(UUID tenantId, String status, UUID patientId, String like) {
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

    public Optional<ReceivableHeadRow> findHead(UUID tenantId, UUID id) {
        return jdbc.sql("""
                        select r.id, r.patient_id, p.full_name as patient_name, r.treatment_plan_id, r.description,
                               r.total, r.status, r.cancel_reason, r.created_at
                        from receivables r join patients p on p.tenant_id = r.tenant_id and p.id = r.patient_id
                        where r.tenant_id = ? and r.id = ?
                        """).params(tenantId, id).query(ReceivableHeadRow.class).optional();
    }

    public void cancel(String reason, UUID tenantId, UUID id) {
        jdbc.sql("""
                        update receivables set status = 'CANCELLED', cancel_reason = ?, version = version + 1
                        where tenant_id = ? and id = ?
                        """).params(reason, tenantId, id).update();
    }

    /** Recalcula o status (aberta/paga) da conta a partir das parcelas em aberto; contas canceladas não mudam. */
    public void refreshStatus(UUID tenantId, UUID receivableId) {
        jdbc.sql("""
                        update receivables r set status = case
                                when not exists (select 1 from installments i where i.tenant_id = r.tenant_id
                                    and i.receivable_id = r.id and i.status = 'OPEN') then 'PAID' else 'OPEN' end,
                            version = version + 1
                        where r.tenant_id = ? and r.id = ? and r.status <> 'CANCELLED'
                        """).params(tenantId, receivableId).update();
    }
}
