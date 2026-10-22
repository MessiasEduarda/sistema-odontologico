package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentView(
        UUID id,
        BigDecimal amount,
        BigDecimal refundedAmount,
        String method,
        Instant paidAt,
        String notes,
        String createdByName) {}
