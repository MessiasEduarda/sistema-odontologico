package br.com.odontosaas.platformadmin.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PlatformEvent(
        UUID id,
        String action,
        String actorEmail,
        String resourceType,
        String resourceId,
        String ip,
        Instant occurredAt) {}
