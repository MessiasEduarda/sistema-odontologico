package br.com.odontosaas.platformadmin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|CANCELLED")
        String status) {}
