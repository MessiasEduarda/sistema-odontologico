package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

public record IssuedDocument(
        UUID id,
        UUID patientId,
        String patientName,
        String professionalName,
        String type,
        String title,
        Instant issuedAt) {}
