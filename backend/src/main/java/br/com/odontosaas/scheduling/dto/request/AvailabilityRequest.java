package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AvailabilityRequest(
        @NotNull UUID professionalId, @Min(5) @Max(480) int durationMinutes) {}
