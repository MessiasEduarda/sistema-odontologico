package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record WaitlistResolveRequest(
        @NotBlank @Pattern(regexp = "SCHEDULED|REMOVED") String status, UUID appointmentId) {}
