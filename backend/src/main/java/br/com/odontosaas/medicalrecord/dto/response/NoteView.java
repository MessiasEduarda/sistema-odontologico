package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NoteView(
        UUID id,
        String content,
        UUID professionalId,
        String professionalName,
        UUID appointmentId,
        UUID amendsNoteId,
        String createdByName,
        Instant createdAt) {}
