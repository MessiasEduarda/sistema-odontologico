package br.com.odontosaas.helpdesk.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PlatformTicketSummary(
        UUID id,
        String protocol,
        UUID tenantId,
        String clinicName,
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
