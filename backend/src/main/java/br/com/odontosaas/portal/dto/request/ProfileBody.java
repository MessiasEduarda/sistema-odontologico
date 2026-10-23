package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProfileBody(@NotNull UUID tenantId, @NotNull UUID patientId) {}
