package br.com.odontosaas.reporting.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.reporting.dto.response.AgendaReport;
import br.com.odontosaas.reporting.dto.response.AgingRow;
import br.com.odontosaas.reporting.dto.response.BirthdayRow;
import br.com.odontosaas.reporting.dto.response.FinancialReport;
import br.com.odontosaas.reporting.dto.response.MonthCount;
import br.com.odontosaas.reporting.dto.response.MonthRow;
import br.com.odontosaas.reporting.dto.response.PatientsReport;
import br.com.odontosaas.reporting.dto.response.ProcedureRow;
import br.com.odontosaas.reporting.dto.response.ProductionReport;
import br.com.odontosaas.reporting.dto.response.ProductionRow;
import br.com.odontosaas.reporting.dto.response.ProfessionalAgenda;
import br.com.odontosaas.reporting.dto.response.StatusCount;
import br.com.odontosaas.reporting.repository.ReportRepository;
import br.com.odontosaas.reporting.repository.ReportRepository.PatientCountsRow;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository repository;
    private final AuditService audit;

    public ReportService(ReportRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) {
            throw ApiException.badRequest("INVALID_PERIOD", "Período inválido (máximo de 400 dias).");
        }
    }

    private String zone(UUID tenantId) {
        return repository.timeZone(tenantId);
    }

    @Transactional(readOnly = true)
    public AgendaReport agenda(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        List<StatusCount> byStatus = repository.appointmentsByStatus(tenantId, zone, from, to);
        long total = byStatus.stream().mapToLong(StatusCount::total).sum();
        Map<String, Long> m = byStatus.stream().collect(Collectors.toMap(StatusCount::status, StatusCount::total));
        long noShow = m.getOrDefault("NO_SHOW", 0L);
        long cancelled = m.getOrDefault("CANCELLED", 0L);
        long attended = m.getOrDefault("COMPLETED", 0L) + noShow;

        List<ProfessionalAgenda> byProfessional = repository.agendaByProfessional(tenantId, zone, from, to);
        return new AgendaReport(
                from, to, total, byStatus, percent(noShow, attended), percent(cancelled, total), byProfessional);
    }

    @Transactional(readOnly = true)
    public ProductionReport production(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        List<ProductionRow> byProfessional = repository.productionByProfessional(tenantId, zone, from, to);
        List<ProcedureRow> top = repository.topProcedures(tenantId, zone, from, to);
        BigDecimal totalValue =
                byProfessional.stream().map(ProductionRow::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProductionReport(from, to, totalValue, byProfessional, top);
    }

    @Transactional(readOnly = true)
    public FinancialReport financial(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        List<MonthRow> months = repository.financialMonths(tenantId, zone, from, to);
        List<AgingRow> aging = repository.installmentsAging(tenantId);
        return new FinancialReport(from, to, months, aging);
    }

    @Transactional(readOnly = true)
    public PatientsReport patients() {
        UUID tenantId = TenantContext.requireTenantId();
        String zone = zone(tenantId);
        PatientCountsRow counts = repository.patientCounts(tenantId);
        List<MonthCount> newByMonth = repository.newPatientsByMonth(tenantId, zone);
        List<BirthdayRow> birthdays = repository.birthdaysThisMonth(tenantId, zone);
        return new PatientsReport(counts.active(), counts.inactive(), newByMonth, birthdays);
    }

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
