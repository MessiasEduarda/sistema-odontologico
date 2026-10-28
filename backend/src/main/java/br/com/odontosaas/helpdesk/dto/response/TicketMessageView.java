package br.com.odontosaas.helpdesk.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TicketMessageView(
        UUID id, String authorType, String authorName, String body, boolean internal, Instant createdAt) {}
