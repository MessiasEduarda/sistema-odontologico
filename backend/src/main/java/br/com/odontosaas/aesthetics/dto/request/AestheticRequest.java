package br.com.odontosaas.aesthetics.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AestheticRequest(
        @NotNull UUID professionalId,
        Instant performedAt,

        @NotBlank @Pattern(regexp = "TOXIN|FILLER|BIOSTIMULATOR|THREADS|SKINBOOSTER|PEEL|MICRONEEDLING|LIPOLYSIS|OTHER")
        String procedureType,

        @NotBlank @Size(max = 160) String productName,
        @Size(max = 160) String manufacturer,
        @Size(max = 40) String anvisaRegistry,
        @NotBlank @Size(max = 60) String batch,
        LocalDate expiryDate,

        @NotBlank @Pattern(regexp = "U|ML|UN|MG") String unit,

        @NotEmpty @Size(max = 40) List<@Valid Region> regions,
        @Size(max = 2000) String technique,
        @Size(max = 2000) String notes,
        LocalDate returnDate,
        UUID consentId) {}
