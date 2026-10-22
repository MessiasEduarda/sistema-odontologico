package br.com.odontosaas.consent.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConsentTemplateView(
        UUID id,
        UUID familyId,
        String title,
        String category,
        String body,
        int version,
        boolean active,
        Instant createdAt) {}
