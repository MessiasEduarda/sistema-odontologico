package br.com.odontosaas.helpdesk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record NewTicketRequest(
        @NotBlank(message = "Informe o assunto.") @Size(max = 160)
        String subject,

        @NotBlank(message = "Escolha a categoria.")
        @Pattern(regexp = "BUG|HOW_TO|ACCESS|BILLING|SUGGESTION|DATA_PRIVACY", message = "Categoria inválida.")
        String category,

        @NotBlank(message = "Diga o quanto isso atrapalha o trabalho.")
        @Pattern(regexp = "BLOCKING|DEGRADED|QUESTION|SUGGESTION", message = "Impacto inválido.")
        String impact,

        @NotBlank(message = "Descreva o que aconteceu.") @Size(max = 5000)
        String description,

        @Size(max = 300) String page,
        @Size(max = 64) String traceId,
        @Size(max = 500) String errorMessage,
        UUID relatedTicketId) {}
