package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** Resolve a entrada: SCHEDULED (com o atendimento marcado) ou REMOVED (desistência). */
public record WaitlistResolveRequest(
        @NotBlank @Pattern(regexp = "SCHEDULED|REMOVED") String status, UUID appointmentId) {}
