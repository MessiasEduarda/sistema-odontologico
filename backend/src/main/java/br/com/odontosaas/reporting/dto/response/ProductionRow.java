package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionRow(UUID professionalId, String professionalName, long procedures, BigDecimal value) {}
