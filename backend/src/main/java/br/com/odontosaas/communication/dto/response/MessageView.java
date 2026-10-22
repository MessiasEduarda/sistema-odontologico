package br.com.odontosaas.communication.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
        UUID id,
        String type,
        String toAddress,
        String subject,
        String status,
        int attempts,
        String lastError,
        String patientName,
        Instant createdAt,
        Instant sentAt) {}
