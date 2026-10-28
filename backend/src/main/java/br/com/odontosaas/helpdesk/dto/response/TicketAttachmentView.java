package br.com.odontosaas.helpdesk.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TicketAttachmentView(
        UUID id,
        String authorType,
        String filename,
        String contentType,
        long sizeBytes,
        Instant createdAt,
        boolean purged) {}
