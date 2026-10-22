package br.com.odontosaas.consent.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ConsentRequest(
        @NotNull UUID templateId,
        UUID professionalId,
        @Size(max = 200) String procedureDescription) {}
