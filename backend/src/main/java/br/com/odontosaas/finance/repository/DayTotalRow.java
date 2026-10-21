package br.com.odontosaas.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Total de um dia (já no fuso da clínica) para o fluxo de caixa. */
public record DayTotalRow(LocalDate day, BigDecimal total) {}
