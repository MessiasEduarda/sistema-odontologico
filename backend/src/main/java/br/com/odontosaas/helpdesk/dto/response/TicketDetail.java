package br.com.odontosaas.helpdesk.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketDetail(
        UUID id,
        String protocol,
        UUID tenantId,
        String clinicName,
        String subject,
        String category,
        String priority,
        String status,
        String openedByName,
        String openedByEmail,
        Instant createdAt,
        Instant firstResponseDueAt,
        Instant firstResponseAt,
        boolean overdue,
        Instant resolvedAt,
        Instant closedAt,
        boolean canReply,
        boolean canRate,
        Integer rating,
        String ratingComment,
        String page,
        String traceId,
        String errorMessage,
        String userAgent,
        UUID relatedTicketId,
        String relatedProtocol,
        List<TicketMessageView> messages,
        List<TicketAttachmentView> attachments) {}
