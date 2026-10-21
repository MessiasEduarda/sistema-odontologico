package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;

public record MonthRow(String month, BigDecimal received, BigDecimal refunded, BigDecimal paidOut) {}
