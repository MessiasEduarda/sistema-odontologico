package br.com.odontosaas.portal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PortalConsent(UUID id, String title, String body, String status, Instant createdAt) {}
