package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashFlowView(
        LocalDate from,
        LocalDate to,
        BigDecimal totalInflow,
        BigDecimal totalOutflow,
        BigDecimal net,
        List<CashFlowDay> days,
        List<MethodTotal> byMethod) {}
