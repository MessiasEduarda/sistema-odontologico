package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AgendaReport(
        LocalDate from,
        LocalDate to,
        long total,
        List<StatusCount> byStatus,
        BigDecimal noShowRate,
        BigDecimal cancellationRate,
        List<ProfessionalAgenda> byProfessional) {}
