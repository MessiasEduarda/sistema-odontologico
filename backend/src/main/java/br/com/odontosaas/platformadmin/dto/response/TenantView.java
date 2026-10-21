package br.com.odontosaas.platformadmin.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TenantView(UUID id, String name, String slug, String status, Instant createdAt) {}
