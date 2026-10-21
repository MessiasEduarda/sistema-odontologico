package br.com.odontosaas.reporting.service;

import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.security.Permission;
import br.com.odontosaas.reporting.dto.response.Dashboard;
import br.com.odontosaas.reporting.dto.response.DashboardAlert;
import br.com.odontosaas.reporting.dto.response.TodayAppointment;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Painel inicial: só inclui indicadores que a pessoa tem permissão de ver. */
@Service
public class DashboardService {

    private final JdbcClient jdbc;

    public DashboardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(AuthenticatedUser user) {
        UUID tenantId = TenantContext.requireTenantId();
        String zone = jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
        boolean schedule = user.has(Permission.SCHEDULE_READ);
        boolean patients = user.has(Permission.PATIENTS_READ);
        boolean finance = user.has(Permission.FINANCE_READ);
        boolean inventory = user.has(Permission.INVENTORY_READ);
        boolean clinical = user.has(Permission.CLINICAL_RECORDS_READ) || user.has(Permission.PATIENTS_WRITE);

        Long appointmentsToday = null, confirmedToday = null, completedToday = null;
        List<TodayAppointment> today = List.of();
        List<DashboardAlert> alerts = new ArrayList<>();
        if (schedule) {
            today = jdbc.sql("""
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
            appointmentsToday = (long) today.size();
            confirmedToday = today.stream()
                    .filter(a -> List.of("CONFIRMED", "ARRIVED", "IN_PROGRESS", "COMPLETED")
                            .contains(a.status()))
                    .count();
            completedToday =
                    today.stream().filter(a -> "COMPLETED".equals(a.status())).count();
            long unconfirmed = count("""
                    select count(*) from appointments where tenant_id = ? and status = 'SCHEDULED'
                      and (starts_at at time zone ?)::date = (now() at time zone ?)::date + 1
                    """, tenantId, zone, zone);
            if (unconfirmed > 0) {
                alerts.add(new DashboardAlert(
                        "UNCONFIRMED_TOMORROW",
                        "warning",
                        unconfirmed + " consulta(s) de amanhã ainda sem confirmação.",
                        unconfirmed));
            }
        }
        Long activePatients = null, newPatients = null;
        if (patients) {
            activePatients = jdbc.sql("select count(*) from patients where tenant_id = ? and active")
                    .param(tenantId)
                    .query(Long.class)
                    .single();
            newPatients = count("""
                    select count(*) from patients where tenant_id = ?
                      and date_trunc('month', created_at at time zone ?) = date_trunc('month', now() at time zone ?)
                    """, tenantId, zone, zone);
        }
        if (clinical) {
            long pending = jdbc.sql("select count(*) from patient_consents where tenant_id = ? and status = 'PENDING'")
                    .param(tenantId)
                    .query(Long.class)
                    .single();
            if (pending > 0) {
                alerts.add(new DashboardAlert(
                        "PENDING_CONSENTS", "info", pending + " termo(s) aguardando assinatura.", pending));
            }
        }
        BigDecimal received = null, overdue = null;
        if (finance) {
            received = jdbc.sql("""
                            select coalesce(sum(amount), 0) - coalesce((select sum(amount) from refunds where tenant_id = ?
                                and date_trunc('month', refunded_at at time zone ?) = date_trunc('month', now() at time zone ?)), 0)
                            from payments where tenant_id = ?
                              and date_trunc('month', paid_at at time zone ?) = date_trunc('month', now() at time zone ?)
                            """)
                    .params(tenantId, zone, zone, tenantId, zone, zone)
                    .query(BigDecimal.class)
                    .single();
            record Overdue(long count, BigDecimal amount) {}
            Overdue o = jdbc.sql("""
                            select count(*) as count, coalesce(sum(amount - paid_amount), 0) as amount from installments
                            where tenant_id = ? and status = 'OPEN' and due_date < current_date
                            """).param(tenantId).query(Overdue.class).single();
            overdue = o.amount();
            if (o.count() > 0) {
                alerts.add(new DashboardAlert(
                        "OVERDUE_INSTALLMENTS", "danger", o.count() + " parcela(s) em atraso.", o.count()));
            }
            long payablesSoon = jdbc.sql("select count(*) from payables where tenant_id = ? and status = 'OPEN'"
                            + " and due_date <= current_date + 3")
                    .param(tenantId)
                    .query(Long.class)
                    .single();
            if (payablesSoon > 0) {
                alerts.add(new DashboardAlert(
                        "PAYABLES_DUE",
                        "warning",
                        payablesSoon + " conta(s) a pagar vencendo em até 3 dias.",
                        payablesSoon));
            }
        }
        Long lowStock = null;
        if (inventory) {
            lowStock = jdbc.sql("select count(*) from products where tenant_id = ? and active"
                            + " and current_stock <= min_stock")
                    .param(tenantId)
                    .query(Long.class)
                    .single();
            if (lowStock > 0) {
                alerts.add(new DashboardAlert(
                        "LOW_STOCK", "warning", lowStock + " produto(s) com estoque baixo.", lowStock));
            }
        }
        return new Dashboard(
                appointmentsToday,
                confirmedToday,
                completedToday,
                activePatients,
                newPatients,
                received,
                overdue,
                lowStock,
                today,
                alerts);
    }

    private long count(String sql, UUID tenantId, String zone1, String zone2) {
        return jdbc.sql(sql).params(tenantId, zone1, zone2).query(Long.class).single();
    }
}
