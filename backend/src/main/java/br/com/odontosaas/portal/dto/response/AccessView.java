package br.com.odontosaas.portal.dto.response;

import java.time.Instant;

public record AccessView(String url, Instant expiresAt, boolean emailQueued) {}
