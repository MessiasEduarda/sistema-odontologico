package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;

public record FinanceSummary(
        BigDecimal receivedThisMonth,
        BigDecimal paidOutThisMonth,
        BigDecimal openReceivables,
        BigDecimal overdueReceivables,
        int overdueInstallments,
        BigDecimal openPayables,
        BigDecimal payablesDueNext7Days) {}
