package br.com.odontosaas.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TicketPriorityRequest(
        @NotBlank @Pattern(regexp = "URGENT|HIGH|NORMAL|LOW", message = "Prioridade inválida.")
        String priority) {}
