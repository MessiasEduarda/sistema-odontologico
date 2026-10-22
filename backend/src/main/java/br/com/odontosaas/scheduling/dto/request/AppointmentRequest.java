package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record AppointmentRequest(
        @NotNull UUID patientId,
        @NotNull UUID professionalId,
        UUID roomId,
        UUID procedureId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 2000) String notes,
        Long version) {}
