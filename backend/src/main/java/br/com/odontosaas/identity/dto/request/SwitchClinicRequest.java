package br.com.odontosaas.identity.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SwitchClinicRequest(@NotNull UUID clinicId) {}
