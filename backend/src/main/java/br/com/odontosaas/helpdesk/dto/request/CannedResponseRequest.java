package br.com.odontosaas.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CannedResponseRequest(
        @NotBlank(message = "Informe o título.") @Size(max = 120)
        String title,

        @NotBlank(message = "Escreva o texto da resposta.") @Size(max = 5000)
        String body,

        Boolean active) {}
