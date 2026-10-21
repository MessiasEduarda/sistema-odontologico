package br.com.odontosaas.treatment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanView(
        UUID id,
        UUID patientId,
        String patientName,
        UUID professionalId,
        String professionalName,
        String title,
        String status,
        BigDecimal subtotal,
        BigDecimal itemsDiscount,
        BigDecimal discount,
        BigDecimal total,
        String paymentConditions,
        String notes,
        LocalDate validUntil,
        Instant approvedAt,
        String approvedByName,
        Instant createdAt,
        long version,
        UUID receivableId,
        List<ItemView> items) {}
