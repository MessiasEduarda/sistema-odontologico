package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StatusRequest(
        @NotBlank @Pattern(regexp = "SCHEDULED|CONFIRMED|ARRIVED|IN_PROGRESS|COMPLETED|NO_SHOW|CANCELLED")
        String status,

        @Size(max = 300) String reason) {}
