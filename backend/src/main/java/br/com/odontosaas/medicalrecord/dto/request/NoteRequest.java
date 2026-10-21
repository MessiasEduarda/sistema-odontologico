package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record NoteRequest(
        @NotBlank @Size(max = 20000) String content, UUID professionalId, UUID appointmentId, UUID amendsNoteId) {}
