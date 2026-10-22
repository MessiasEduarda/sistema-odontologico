package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PerformedView(
        UUID id,
        UUID professionalId,
        String professionalName,
        UUID procedureId,
        String description,
        Integer tooth,
        String surfaces,
        UUID treatmentItemId,
        Instant performedAt,
        String notes,
        Instant voidedAt,
        String voidReason) {}
