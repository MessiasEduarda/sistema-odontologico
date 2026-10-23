package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AttachmentView(
        UUID id,
        String category,
        String description,
        String filename,
        String contentType,
        long sizeBytes,
        boolean sharedWithPatient,
        String uploadedByName,
        Instant createdAt) {}
