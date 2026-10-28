package br.com.odontosaas.helpdesk.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CannedResponseView(UUID id, String title, String body, boolean active, Instant updatedAt) {}
