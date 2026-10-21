package br.com.odontosaas.reporting.repository;

import br.com.odontosaas.reporting.dto.response.TodayAppointment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas agregadas do painel inicial (somente leitura, sempre filtradas pela clínica). */
@Repository
public class DashboardRepository {

    /** Parcelas em atraso: quantidade e valor em aberto. */
    public record OverdueRow(long count, BigDecimal amount) {}

    private final JdbcClient jdbc;

    public DashboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String timeZone(UUID tenantId) {
        return jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    public List<TodayAppointment> todayAppointments(UUID tenantId, String zone) {
        return jdbc.sql("""
                        select a.id, a.starts_at, a.ends_at, p.full_name as patient_name, p.id as patient_id,
                               pr.name as professional_name, pr.color as professional_color,
                               pc.name as procedure_name, a.status
                        from appointments a
                        join patients p on p.tenant_id = a.tenant_id and p.id = a.patient_id
                        join professionals pr on pr.tenant_id = a.tenant_id and pr.id = a.professional_id
                        left join procedures pc on pc.tenant_id = a.tenant_id and pc.id = a.procedure_id
                        where a.tenant_id = ? and (a.starts_at at time zone ?)::date = (now() at time zone ?)::date
                          and a.status <> 'CANCELLED'
                        order by a.starts_at
                        """)
                .params(tenantId, zone, zone)
                .query(TodayAppointment.class)
                .list();
    }

    public long countUnconfirmedTomorrow(UUID tenantId, String zone) {
        return count("""
                select count(*) from appointments where tenant_id = ? and status = 'SCHEDULED'
                  and (starts_at at time zone ?)::date = (now() at time zone ?)::date + 1
                """, tenantId, zone, zone);
    }

    public long countActivePatients(UUID tenantId) {
        return jdbc.sql("select count(*) from patients where tenant_id = ? and active")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    public long countNewPatientsThisMonth(UUID tenantId, String zone) {
        return count("""
                select count(*) from patients where tenant_id = ?
                  and date_trunc('month', created_at at time zone ?) = date_trunc('month', now() at time zone ?)
                """, tenantId, zone, zone);
    }

    public long countPendingConsents(UUID tenantId) {
        return jdbc.sql("select count(*) from patient_consents where tenant_id = ? and status = 'PENDING'")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    public BigDecimal receivedThisMonth(UUID tenantId, String zone) {
        return jdbc.sql("""
                        select coalesce(sum(amount), 0) - coalesce((select sum(amount) from refunds where tenant_id = ?
                            and date_trunc('month', refunded_at at time zone ?) = date_trunc('month', now() at time zone ?)), 0)
                        from payments where tenant_id = ?
                          and date_trunc('month', paid_at at time zone ?) = date_trunc('month', now() at time zone ?)
                        """)
                .params(tenantId, zone, zone, tenantId, zone, zone)
                .query(BigDecimal.class)
                .single();
    }

    public OverdueRow overdueInstallments(UUID tenantId) {
        return jdbc.sql("""
                        select count(*) as count, coalesce(sum(amount - paid_amount), 0) as amount from installments
                        where tenant_id = ? and status = 'OPEN' and due_date < current_date
                        """).param(tenantId).query(OverdueRow.class).single();
    }

    public long countPayablesDueSoon(UUID tenantId) {
        return jdbc.sql("select count(*) from payables where tenant_id = ? and status = 'OPEN'"
                        + " and due_date <= current_date + 3")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    public long countLowStock(UUID tenantId) {
        return jdbc.sql("select count(*) from products where tenant_id = ? and active"
                        + " and current_stock <= min_stock")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    private long count(String sql, UUID tenantId, String zone1, String zone2) {
        return jdbc.sql(sql).params(tenantId, zone1, zone2).query(Long.class).single();
    }
}
