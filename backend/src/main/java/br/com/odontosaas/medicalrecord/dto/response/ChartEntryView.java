package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ChartEntryView(
        UUID id,
        int tooth,
        String surfaces,
        String condition,
        String status,
        String notes,
        String createdByName,
        Instant createdAt,
        Instant voidedAt,
        String voidReason) {}
