package br.com.odontosaas.consent.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Item da lista geral de termos da clínica (página Termos). */
public record ConsentListItem(
        UUID id,
        UUID patientId,
        String patientName,
        String title,
        String category,
        String status,
        String professionalName,
        String procedureDescription,
        Instant createdAt,
        Instant signedAt) {}
