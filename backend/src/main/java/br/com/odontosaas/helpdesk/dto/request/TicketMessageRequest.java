package br.com.odontosaas.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketMessageRequest(
        @NotBlank(message = "Escreva a mensagem.") @Size(max = 5000)
        String body) {}
