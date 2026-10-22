package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;

public record ProcedureRow(String description, long total, BigDecimal value) {}
