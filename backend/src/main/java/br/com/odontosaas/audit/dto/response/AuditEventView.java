package br.com.odontosaas.audit.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuditEventView(
        UUID id,
        String action,
        String actorName,
        String actorEmail,
        String resourceType,
        String resourceId,
        String ip,
        String metadata,
        Instant occurredAt) {}
