package br.com.odontosaas.consent.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Categoria: DENTAL, AESTHETIC (harmonização), IMAGE (uso de imagem), PRIVACY (LGPD) ou GENERAL. */
public record ConsentTemplateView(
        UUID id,
        UUID familyId,
        String title,
        String category,
        String body,
        int version,
        boolean active,
        Instant createdAt) {}
