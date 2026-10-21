package br.com.odontosaas.finance.repository;

import br.com.odontosaas.finance.dto.response.FinanceSummary;
import br.com.odontosaas.finance.dto.response.MethodTotal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas de leitura para o fluxo de caixa e o resumo financeiro (payments, refunds, payables e
 * installments). Datas "do dia" seguem o fuso da clínica.
 */
@Repository
public class CashFlowRepository {

    private final JdbcClient jdbc;

    public CashFlowRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Fuso horário da clínica. */
    public String timeZone(UUID tenantId) {
        return jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    /** Recebimentos por dia no período. */
    public List<DayTotalRow> paymentTotalsByDay(String zone, UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select (paid_at at time zone ?)::date as day, sum(amount) as total from payments
                        where tenant_id = ? and (paid_at at time zone ?)::date between ? and ? group by 1
                        """)
                .params(zone, tenantId, zone, from, to)
                .query((rs, n) -> new DayTotalRow(rs.getObject("day", LocalDate.class), rs.getBigDecimal("total")))
                .list();
    }

    /** Estornos por dia no período. */
    public List<DayTotalRow> refundTotalsByDay(String zone, UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select (refunded_at at time zone ?)::date as day, sum(amount) as total from refunds
                        where tenant_id = ? and (refunded_at at time zone ?)::date between ? and ? group by 1
                        """)
                .params(zone, tenantId, zone, from, to)
                .query((rs, n) -> new DayTotalRow(rs.getObject("day", LocalDate.class), rs.getBigDecimal("total")))
                .list();
    }

    /** Contas a pagar quitadas por dia no período. */
    public List<DayTotalRow> paidPayableTotalsByDay(String zone, UUID tenantId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select (paid_at at time zone ?)::date as day, sum(amount) as total from payables
                        where tenant_id = ? and status = 'PAID' and (paid_at at time zone ?)::date between ? and ?
                        group by 1
                        """)
                .params(zone, tenantId, zone, from, to)
                .query((rs, n) -> new DayTotalRow(rs.getObject("day", LocalDate.class), rs.getBigDecimal("total")))
                .list();
    }

    /** Total líquido (recebido menos estornado) por forma de pagamento no período. */
    public List<MethodTotal> totalsByMethod(UUID tenantId, String zone, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select method, sum(amount - refunded_amount) as amount from payments
                        where tenant_id = ? and (paid_at at time zone ?)::date between ? and ?
                        group by method order by 2 desc
                        """)
                .params(tenantId, zone, from, to)
                .query(MethodTotal.class)
                .list();
    }

    public FinanceSummary summary(UUID tenantId, String zone) {
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
}
