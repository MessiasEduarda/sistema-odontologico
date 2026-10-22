package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenBody(@NotBlank @Size(max = 64) String token) {}
