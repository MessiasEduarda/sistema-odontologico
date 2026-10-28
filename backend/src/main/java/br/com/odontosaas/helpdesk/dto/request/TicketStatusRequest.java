package br.com.odontosaas.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TicketStatusRequest(
        @NotBlank @Pattern(regexp = "IN_PROGRESS|WAITING_CLINIC|RESOLVED|CLOSED", message = "Situação inválida.")
        String status) {}
