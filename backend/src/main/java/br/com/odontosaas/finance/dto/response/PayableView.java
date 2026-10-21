package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayableView(
        UUID id,
        UUID supplierId,
        String supplierName,
        String description,
        String category,
        BigDecimal amount,
        LocalDate dueDate,
        String status,
        boolean overdue,
        Instant paidAt,
        String paymentMethod,
        String notes) {}
