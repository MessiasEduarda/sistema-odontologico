package br.com.odontosaas.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupportReplyRequest(
        @NotBlank(message = "Escreva a mensagem.") @Size(max = 5000)
        String body,

        Boolean internal,

        @Pattern(regexp = "IN_PROGRESS|WAITING_CLINIC|RESOLVED", message = "Situação inválida.")
        String status) {}
