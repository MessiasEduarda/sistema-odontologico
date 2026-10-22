package br.com.odontosaas.finance.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceivableHeadRow(
        UUID id,
        UUID patientId,
        String patientName,
        UUID treatmentPlanId,
        String description,
        BigDecimal total,
        String status,
        String cancelReason,
        Instant createdAt) {}
