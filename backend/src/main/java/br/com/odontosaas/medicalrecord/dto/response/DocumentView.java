package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DocumentView(
        UUID id,
        UUID professionalId,
        String professionalName,
        String professionalCro,
        String type,
        String title,
        String content,
        Instant issuedAt) {}
