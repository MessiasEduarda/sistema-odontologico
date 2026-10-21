package br.com.odontosaas.reporting.controller;

import br.com.odontosaas.identity.security.CurrentUser;
import br.com.odontosaas.reporting.dto.response.AgendaReport;
import br.com.odontosaas.reporting.dto.response.Dashboard;
import br.com.odontosaas.reporting.dto.response.FinancialReport;
import br.com.odontosaas.reporting.dto.response.PatientsReport;
import br.com.odontosaas.reporting.dto.response.ProductionReport;
import br.com.odontosaas.reporting.service.DashboardService;
import br.com.odontosaas.reporting.service.ReportService;
import br.com.odontosaas.shared.Access;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Relatórios e dashboard")
public class ReportController {

    private static final String REPORTS = "hasAuthority('reports:read')";

    private final ReportService reports;
    private final DashboardService dashboard;

    public ReportController(ReportService reports, DashboardService dashboard) {
        this.reports = reports;
        this.dashboard = dashboard;
    }

    @GetMapping("/api/v1/dashboard")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Painel inicial (conteúdo varia conforme as permissões)")
    public Dashboard dashboard() {
        return dashboard.dashboard(CurrentUser.require());
    }

    @GetMapping("/api/v1/reports/agenda")
    @PreAuthorize(REPORTS)
    @Operation(summary = "Ocupação, faltas e cancelamentos por profissional")
    public AgendaReport agenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.agenda(from, to);
    }

    @GetMapping("/api/v1/reports/production")
    @PreAuthorize(REPORTS)
    @Operation(summary = "Produção por profissional e procedimentos mais realizados")
    public ProductionReport production(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.production(from, to);
    }

    @GetMapping("/api/v1/reports/financial")
    @PreAuthorize(REPORTS + " and hasAuthority('finance:read')")
    @Operation(summary = "Recebimentos/pagamentos por mês e inadimplência por faixa de atraso")
    public FinancialReport financial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.financial(from, to);
    }

    @GetMapping("/api/v1/reports/patients")
    @PreAuthorize(REPORTS + " and hasAuthority('patients:read')")
    public PatientsReport patients() {
        return reports.patients();
    }

    @GetMapping("/api/v1/reports/{type}/export")
    @PreAuthorize(REPORTS)
    @Operation(summary = "Exporta CSV (agenda, production, financial). Auditado.")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if ("financial".equals(type)
                && !CurrentUser.require().has(br.com.odontosaas.identity.security.Permission.FINANCE_READ)) {
            throw br.com.odontosaas.shared.ApiException.forbidden();
        }
        String csv = reports.exportCsv(type, from, to);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("relatorio-" + type + "-" + from + "-" + to + ".csv")
                                .build()
                                .toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
