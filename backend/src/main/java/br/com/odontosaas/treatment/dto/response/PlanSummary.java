package br.com.odontosaas.treatment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanSummary(
        UUID id,
        UUID patientId,
        String patientName,
        UUID professionalId,
        String professionalName,
        String title,
        String status,
        BigDecimal total,
        int itemsTotal,
        int itemsDone,
        Instant createdAt,
        Instant approvedAt) {}
