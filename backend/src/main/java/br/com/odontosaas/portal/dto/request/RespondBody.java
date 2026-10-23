package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record RespondBody(
        @NotNull UUID appointmentId,
        @NotBlank @Pattern(regexp = "CONFIRM|CANCEL") String action) {}
