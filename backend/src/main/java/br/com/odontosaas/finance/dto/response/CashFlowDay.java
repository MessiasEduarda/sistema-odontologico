package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowDay(LocalDate date, BigDecimal inflow, BigDecimal outflow, BigDecimal net) {}
