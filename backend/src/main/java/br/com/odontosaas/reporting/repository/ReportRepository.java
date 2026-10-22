package br.com.odontosaas.reporting.repository;

import br.com.odontosaas.reporting.dto.response.AgingRow;
import br.com.odontosaas.reporting.dto.response.BirthdayRow;
import br.com.odontosaas.reporting.dto.response.MonthCount;
import br.com.odontosaas.reporting.dto.response.MonthRow;
import br.com.odontosaas.reporting.dto.response.ProcedureRow;
import br.com.odontosaas.reporting.dto.response.ProductionRow;
import br.com.odontosaas.reporting.dto.response.ProfessionalAgenda;
import br.com.odontosaas.reporting.dto.response.StatusCount;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

    public record PatientCountsRow(long active, long inactive) {}

    private static final String PRODUCTION_BASE = """
            select x.professional_id, p.name as professional_name, x.description,
                   coalesce(i.quantity * i.unit_price - i.discount, pc.default_price, 0) as value
            from performed_procedures x
            join professionals p on p.tenant_id = x.tenant_id and p.id = x.professional_id
            left join treatment_plan_items i on i.tenant_id = x.tenant_id and i.id = x.treatment_item_id
            left join procedures pc on pc.tenant_id = x.tenant_id and pc.id = x.procedure_id
            where x.tenant_id = ? and x.voided_at is null and (x.performed_at at time zone ?)::date between ? and ?
            """;

    private final JdbcClient jdbc;

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String timeZone(UUID tenantId) {
        return jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    public List<StatusCount> appointmentsByStatus(UUID tenantId, String zone, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select status, count(*) as total from appointments
                        where tenant_id = ? and (starts_at at time zone ?)::date between ? and ?
                        group by status order by total desc
                        """)
                .params(tenantId, zone, from, to)
                .query(StatusCount.class)
                .list();
    }

    public List<ProfessionalAgenda> agendaByProfessional(UUID tenantId, String zone, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        with days as (
                            select d::date as day, extract(isodow from d)::int as weekday
                            from generate_series(cast(? as date), cast(? as date), interval '1 day') d
                        ),
                        available as (
                            select s.professional_id,
                                   sum(extract(epoch from (s.end_time - s.start_time)) / 60)::bigint as minutes
                            from professional_schedules s join days on days.weekday = s.weekday
                            where s.tenant_id = ? group by s.professional_id
                        ),
                        booked as (
                            select professional_id, count(*) as appointments,
                                   count(*) filter (where status = 'COMPLETED') as completed,
                                   count(*) filter (where status = 'NO_SHOW') as no_shows,
                                   count(*) filter (where status = 'CANCELLED') as cancelled,
                                   coalesce(sum(extract(epoch from (ends_at - starts_at)) / 60)
                                       filter (where status not in ('CANCELLED', 'NO_SHOW')), 0)::bigint as booked_minutes
                            from appointments
                            where tenant_id = ? and (starts_at at time zone ?)::date between ? and ?
                            group by professional_id
                        )
                        select p.id as professional_id, p.name as professional_name,
                               coalesce(b.appointments, 0) as appointments, coalesce(b.completed, 0) as completed,
                               coalesce(b.no_shows, 0) as no_shows, coalesce(b.cancelled, 0) as cancelled,
                               coalesce(b.booked_minutes, 0) as booked_minutes,
                               coalesce(a.minutes, 0) as available_minutes,
                               case when coalesce(a.minutes, 0) = 0 then 0
                                    else round(100.0 * coalesce(b.booked_minutes, 0) / a.minutes, 1) end
                                   as occupancy_percent
                        from professionals p
                        left join available a on a.professional_id = p.id
                        left join booked b on b.professional_id = p.id
                        where p.tenant_id = ? and (p.active or b.appointments > 0)
                        order by p.name
                        """)
                .params(from, to, tenantId, tenantId, zone, from, to, tenantId)
                .query(ProfessionalAgenda.class)
                .list();
    }

    public List<ProductionRow> productionByProfessional(UUID tenantId, String zone, LocalDate from, LocalDate to) {
        return jdbc.sql("select professional_id, professional_name, count(*) as procedures, sum(value) as value from ("
                        + PRODUCTION_BASE
                        + ") t group by professional_id, professional_name order by value desc")
                .params(tenantId, zone, from, to)
                .query(ProductionRow.class)
                .list();
    }

    public List<ProcedureRow> topProcedures(UUID tenantId, String zone, LocalDate from, LocalDate to) {
        return jdbc.sql("select description, count(*) as total, sum(value) as value from (" + PRODUCTION_BASE
                        + ") t group by description order by total desc limit 15")
                .params(tenantId, zone, from, to)
                .query(ProcedureRow.class)
                .list();
    }

    public List<MonthRow> financialMonths(UUID tenantId, String zone, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        with m as (
                            select to_char(d, 'YYYY-MM') as month
                            from generate_series(date_trunc('month', cast(? as date)), cast(? as date), interval '1 month') d
                        )
                        select m.month,
                          (select coalesce(sum(amount), 0) from payments where tenant_id = ?
                             and to_char(paid_at at time zone ?, 'YYYY-MM') = m.month) as received,
                          (select coalesce(sum(amount), 0) from refunds where tenant_id = ?
                             and to_char(refunded_at at time zone ?, 'YYYY-MM') = m.month) as refunded,
                          (select coalesce(sum(amount), 0) from payables where tenant_id = ? and status = 'PAID'
                             and to_char(paid_at at time zone ?, 'YYYY-MM') = m.month) as paid_out
                        from m order by m.month
                        """)
                .params(from, to, tenantId, zone, tenantId, zone, tenantId, zone)
                .query(MonthRow.class)
                .list();
    }

    public List<AgingRow> installmentsAging(UUID tenantId) {
        return jdbc.sql("""
                select bucket, count(*) as installments, sum(amount - paid_amount) as amount from (
                    select amount, paid_amount, case
                        when current_date - due_date <= 30 then '1-30 dias'
                        when current_date - due_date <= 60 then '31-60 dias'
                        when current_date - due_date <= 90 then '61-90 dias'
                        else 'mais de 90 dias' end as bucket
                    from installments where tenant_id = ? and status = 'OPEN' and due_date < current_date
                ) t group by bucket order by min(case bucket when '1-30 dias' then 1 when '31-60 dias' then 2
                    when '61-90 dias' then 3 else 4 end)
                """).param(tenantId).query(AgingRow.class).list();
    }

    public PatientCountsRow patientCounts(UUID tenantId) {
        return jdbc.sql(
                        "select count(*) filter (where active) as active, count(*) filter (where not active) as inactive"
                                + " from patients where tenant_id = ?")
                .param(tenantId)
                .query(PatientCountsRow.class)
                .single();
    }

    public List<MonthCount> newPatientsByMonth(UUID tenantId, String zone) {
        return jdbc.sql("""
                select to_char(created_at at time zone ?, 'YYYY-MM') as month, count(*) as total
                from patients where tenant_id = ? and created_at > now() - interval '12 months'
                group by 1 order by 1
                """).params(zone, tenantId).query(MonthCount.class).list();
    }

    public List<BirthdayRow> birthdaysThisMonth(UUID tenantId, String zone) {
        return jdbc.sql("""
                select id, full_name, birth_date, mobile from patients
                where tenant_id = ? and active and birth_date is not null
                  and extract(month from birth_date) = extract(month from now() at time zone ?)
                order by extract(day from birth_date), full_name
                """).params(tenantId, zone).query(BirthdayRow.class).list();
    }
}
