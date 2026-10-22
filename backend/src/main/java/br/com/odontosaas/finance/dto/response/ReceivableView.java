package br.com.odontosaas.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceivableView(
        UUID id,
        UUID patientId,
        String patientName,
        UUID treatmentPlanId,
        String description,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal balance,
        String status,
        String cancelReason,
        Instant createdAt,
        List<InstallmentView> installments) {}
