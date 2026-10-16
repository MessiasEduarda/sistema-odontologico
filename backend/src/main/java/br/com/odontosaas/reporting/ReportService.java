package br.com.odontosaas.reporting;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relatórios gerenciais (somente leitura, sempre na clínica ativa, datas no fuso da clínica). */
@Service
public class ReportService {

    public record StatusCount(String status, long total) {}

    public record ProfessionalAgenda(
            UUID professionalId,
            String professionalName,
            long appointments,
            long completed,
            long noShows,
            long cancelled,
            long bookedMinutes,
            long availableMinutes,
            BigDecimal occupancyPercent) {}

    public record AgendaReport(
            LocalDate from,
            LocalDate to,
            long total,
            List<StatusCount> byStatus,
            BigDecimal noShowRate,
            BigDecimal cancellationRate,
            List<ProfessionalAgenda> byProfessional) {}

    public record ProductionRow(UUID professionalId, String professionalName, long procedures, BigDecimal value) {}

    public record ProcedureRow(String description, long total, BigDecimal value) {}

    public record ProductionReport(
            LocalDate from,
            LocalDate to,
            BigDecimal totalValue,
            List<ProductionRow> byProfessional,
            List<ProcedureRow> topProcedures) {}

    public record MonthRow(String month, BigDecimal received, BigDecimal refunded, BigDecimal paidOut) {}

    public record AgingRow(String bucket, long installments, BigDecimal amount) {}

    public record FinancialReport(LocalDate from, LocalDate to, List<MonthRow> months, List<AgingRow> overdueAging) {}

    public record PatientsReport(
            long active, long inactive, List<MonthCount> newByMonth, List<BirthdayRow> birthdaysThisMonth) {}

    public record MonthCount(String month, long total) {}

    public record BirthdayRow(UUID id, String fullName, LocalDate birthDate, String mobile) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public ReportService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Período inválido (máximo de 400 dias).");
        }
    }

    private String zone(UUID tenantId) {
        return jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    @Transactional(readOnly = true)
    public AgendaReport agenda(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        List<StatusCount> byStatus = jdbc.sql("""
                        select status, count(*) as total from appointments
                        where tenant_id = ? and (starts_at at time zone ?)::date between ? and ?
                        group by status order by total desc
                        """)
                .params(tenantId, zone, from, to)
                .query(StatusCount.class)
                .list();
        long total = byStatus.stream().mapToLong(StatusCount::total).sum();
        Map<String, Long> m = byStatus.stream().collect(Collectors.toMap(StatusCount::status, StatusCount::total));
        long noShow = m.getOrDefault("NO_SHOW", 0L);
        long cancelled = m.getOrDefault("CANCELLED", 0L);
        long attended = m.getOrDefault("COMPLETED", 0L) + noShow;

        List<ProfessionalAgenda> byProfessional = jdbc.sql("""
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
        return new AgendaReport(
                from, to, total, byStatus, percent(noShow, attended), percent(cancelled, total), byProfessional);
    }

    @Transactional(readOnly = true)
    public ProductionReport production(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        // Valor: preço do item do orçamento (quando vinculado) ou preço de tabela do procedimento.
        String base = """
                select x.professional_id, p.name as professional_name, x.description,
                       coalesce(i.quantity * i.unit_price - i.discount, pc.default_price, 0) as value
                from performed_procedures x
                join professionals p on p.tenant_id = x.tenant_id and p.id = x.professional_id
                left join treatment_plan_items i on i.tenant_id = x.tenant_id and i.id = x.treatment_item_id
                left join procedures pc on pc.tenant_id = x.tenant_id and pc.id = x.procedure_id
                where x.tenant_id = ? and x.voided_at is null and (x.performed_at at time zone ?)::date between ? and ?
                """;
        List<ProductionRow> byProfessional = jdbc.sql(
                        "select professional_id, professional_name, count(*) as procedures, sum(value) as value from ("
                                + base
                                + ") t group by professional_id, professional_name order by value desc")
                .params(tenantId, zone, from, to)
                .query(ProductionRow.class)
                .list();
        List<ProcedureRow> top = jdbc.sql("select description, count(*) as total, sum(value) as value from (" + base
                        + ") t group by description order by total desc limit 15")
                .params(tenantId, zone, from, to)
                .query(ProcedureRow.class)
                .list();
        BigDecimal totalValue =
                byProfessional.stream().map(ProductionRow::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProductionReport(from, to, totalValue, byProfessional, top);
    }

    @Transactional(readOnly = true)
    public FinancialReport financial(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        List<MonthRow> months = jdbc.sql("""
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
        List<AgingRow> aging =
                jdbc.sql("""
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
        return new FinancialReport(from, to, months, aging);
    }

    @Transactional(readOnly = true)
    public PatientsReport patients() {
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        record Counts(long active, long inactive) {}
        Counts counts = jdbc.sql(
                        "select count(*) filter (where active) as active, count(*) filter (where not active) as inactive"
                                + " from patients where tenant_id = ?")
                .param(tenantId)
                .query(Counts.class)
                .single();
        List<MonthCount> newByMonth =
                jdbc.sql("""
                        select to_char(created_at at time zone ?, 'YYYY-MM') as month, count(*) as total
                        from patients where tenant_id = ? and created_at > now() - interval '12 months'
                        group by 1 order by 1
                        """).params(zone, tenantId).query(MonthCount.class).list();
        List<BirthdayRow> birthdays =
                jdbc.sql("""
                        select id, full_name, birth_date, mobile from patients
                        where tenant_id = ? and active and birth_date is not null
                          and extract(month from birth_date) = extract(month from now() at time zone ?)
                        order by extract(day from birth_date), full_name
                        """).params(tenantId, zone).query(BirthdayRow.class).list();
        return new PatientsReport(counts.active(), counts.inactive(), newByMonth, birthdays);
    }

    /** Exportação CSV (separador ";" e BOM para abrir corretamente no Excel). Auditada. */
    public String exportCsv(String type, LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder("﻿");
        switch (type) {
            case "agenda" -> {
                AgendaReport r = agenda(from, to);
                csv.append("Profissional;Atendimentos;Concluídos;Faltas;Cancelados;Minutos agendados;"
                        + "Minutos disponíveis;Ocupação (%)\n");
                for (ProfessionalAgenda p : r.byProfessional()) {
                    csv.append(String.join(
                                    ";",
                                    cell(p.professionalName()),
                                    String.valueOf(p.appointments()),
                                    String.valueOf(p.completed()),
                                    String.valueOf(p.noShows()),
                                    String.valueOf(p.cancelled()),
                                    String.valueOf(p.bookedMinutes()),
                                    String.valueOf(p.availableMinutes()),
                                    p.occupancyPercent().toPlainString().replace('.', ',')))
                            .append('\n');
                }
            }
            case "production" -> {
                ProductionReport r = production(from, to);
                csv.append("Profissional;Procedimentos;Valor (R$)\n");
                for (ProductionRow p : r.byProfessional()) {
                    csv.append(cell(p.professionalName()))
                            .append(';')
                            .append(p.procedures())
                            .append(';')
                            .append(p.value().toPlainString().replace('.', ','))
                            .append('\n');
                }
            }
            case "financial" -> {
                FinancialReport r = financial(from, to);
                csv.append("Mês;Recebido (R$);Estornado (R$);Pago (R$)\n");
                for (MonthRow m : r.months()) {
                    csv.append(m.month())
                            .append(';')
                            .append(m.received().toPlainString().replace('.', ','))
                            .append(';')
                            .append(m.refunded().toPlainString().replace('.', ','))
                            .append(';')
                            .append(m.paidOut().toPlainString().replace('.', ','))
                            .append('\n');
                }
            }
            default -> throw ApiException.notFound("Relatório");
        }
        audit.record(AuditAction.REPORT_EXPORTED, "report", type, Map.of("from", from.toString(), "to", to.toString()));
        return csv.toString();
    }

    /** Neutraliza fórmulas (CSV injection) e escapa aspas/separador. */
    static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) v = "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
