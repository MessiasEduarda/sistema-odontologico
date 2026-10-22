package br.com.odontosaas.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidRequest(@NotBlank @Size(max = 300) String reason) {}
