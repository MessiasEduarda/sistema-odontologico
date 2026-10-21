package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Modelo de orientação. moment: BEFORE (pré), AFTER (pós) ou GENERAL. */
public record CareTemplateView(
        UUID id, String title, String category, String moment, String body, boolean active, Instant updatedAt) {}
