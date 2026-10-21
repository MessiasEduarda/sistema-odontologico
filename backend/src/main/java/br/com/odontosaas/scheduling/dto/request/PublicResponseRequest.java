package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PublicResponseRequest(
        @NotBlank @Size(max = 64) String token,
        @NotBlank @Pattern(regexp = "CONFIRM|CANCEL") String action) {}
