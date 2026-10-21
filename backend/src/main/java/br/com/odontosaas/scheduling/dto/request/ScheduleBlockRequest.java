package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Bloqueio de agenda. {@code professionalId} nulo = clínica inteira. */
public record ScheduleBlockRequest(
        UUID professionalId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 160) String reason) {}
