package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record PerformedRequest(
        @NotNull UUID professionalId,
        UUID procedureId,
        @NotBlank @Size(max = 200) String description,
        @Min(11) @Max(85) Integer tooth,
        @Pattern(regexp = "^[MODVLPI]{1,6}$") String surfaces,
        UUID treatmentItemId,
        Instant performedAt,
        @Size(max = 1000) String notes) {}
