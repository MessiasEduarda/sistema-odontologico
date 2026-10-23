package br.com.odontosaas.portal.dto.response;

import java.time.Instant;

public record PortalAccountStatus(
        String status, String email, Instant invitedAt, Instant activatedAt, Instant lastLoginAt) {}
