package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InstallmentView(
        UUID id,
        int number,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status,
        boolean overdue,
        List<PaymentView> payments) {}
