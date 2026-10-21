package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RespondBody(
        @NotBlank @Size(max = 64) String token,
        @NotNull UUID appointmentId,
        @NotBlank @Pattern(regexp = "CONFIRM|CANCEL") String action) {}
