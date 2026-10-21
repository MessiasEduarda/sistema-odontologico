package br.com.odontosaas.treatment.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Cabeçalho de um plano de tratamento (sem itens), como lido do banco. */
public record TreatmentPlanHeadRow(
        UUID id,
        UUID patientId,
        String patientName,
        UUID professionalId,
        String professionalName,
        String title,
        String status,
        BigDecimal discount,
        String paymentConditions,
        String notes,
        LocalDate validUntil,
        Instant approvedAt,
        String approvedByName,
        Instant createdAt,
        long version,
        UUID receivableId) {}
