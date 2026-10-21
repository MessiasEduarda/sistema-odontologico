package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableSummary(
        UUID id,
        UUID patientId,
        String patientName,
        UUID treatmentPlanId,
        String description,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal balance,
        String status,
        LocalDate nextDueDate,
        int overdueInstallments,
        Instant createdAt) {}
