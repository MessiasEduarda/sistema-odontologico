package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChartEntryRequest(
        @Min(11) @Max(85) int tooth,

        @Pattern(regexp = "^[MODVLPI]{1,6}$", message = "faces: M, O, D, V, L, P, I")
        String surfaces,

        @NotBlank String condition,

        @NotBlank @Pattern(regexp = "EXISTING|PLANNED|DONE") String status,

        @Size(max = 500) String notes) {}
