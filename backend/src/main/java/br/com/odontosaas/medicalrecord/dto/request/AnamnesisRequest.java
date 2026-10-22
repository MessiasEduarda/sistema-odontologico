package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record AnamnesisRequest(
        UUID formId,
        @NotNull Map<String, @Valid Answer> answers,
        @Size(max = 4000) String notes,
        @Pattern(regexp = "PROFESSIONAL|PATIENT") String filledBy) {}
