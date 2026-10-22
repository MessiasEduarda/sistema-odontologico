package br.com.odontosaas.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InvitationView(
        UUID id, String email, String roleCode, String status, Instant expiresAt, Instant createdAt) {}
