package br.com.odontosaas.helpdesk.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TicketSummary(
        UUID id,
        String protocol,
        String subject,
        String category,
        String priority,
        String status,
        String openedByName,
        Instant createdAt,
        Instant lastActivityAt,
        Instant firstResponseDueAt,
        Instant firstResponseAt,
        boolean overdue,
        int unreadCount) {}
