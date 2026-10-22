package br.com.odontosaas.consent.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConsentView(
        UUID id,
        String title,
        String category,
        String body,
        String bodySha256,
        Integer templateVersion,
        String procedureDescription,
        String professionalName,
        String status,
        Instant signedAt,
        String signerName,
        String signerDocument,
        String signatureImage,
        String signedIp,
        Instant revokedAt,
        String revokeReason,
        Instant createdAt) {}
