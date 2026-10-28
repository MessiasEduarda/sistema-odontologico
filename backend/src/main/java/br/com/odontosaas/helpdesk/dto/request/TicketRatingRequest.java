package br.com.odontosaas.helpdesk.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketRatingRequest(
        @NotNull(message = "Escolha uma nota.") @Min(1) @Max(5)
        Integer rating,

        @Size(max = 500) String comment) {}
