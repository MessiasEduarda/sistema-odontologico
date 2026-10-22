package br.com.odontosaas.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DayTotalRow(LocalDate day, BigDecimal total) {}
