package br.com.odontosaas.consent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicTokenRequest(@NotBlank @Size(max = 64) String token) {}
