package br.com.odontosaas.reporting.dto.response;

import java.time.LocalDate;
import java.util.List;

public record FinancialReport(LocalDate from, LocalDate to, List<MonthRow> months, List<AgingRow> overdueAging) {}
