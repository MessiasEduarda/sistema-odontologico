package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductionReport(
        LocalDate from,
        LocalDate to,
        BigDecimal totalValue,
        List<ProductionRow> byProfessional,
        List<ProcedureRow> topProcedures) {}
