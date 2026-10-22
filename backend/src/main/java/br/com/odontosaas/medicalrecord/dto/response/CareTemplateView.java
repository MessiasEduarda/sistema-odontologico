package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CareTemplateView(
        UUID id, String title, String category, String moment, String body, boolean active, Instant updatedAt) {}
