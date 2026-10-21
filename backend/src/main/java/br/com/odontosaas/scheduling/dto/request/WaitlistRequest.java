package br.com.odontosaas.scheduling.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Entrada na lista de espera. Profissional e procedimento são opcionais (qualquer um serve). */
public record WaitlistRequest(
        @NotNull UUID patientId,
        UUID professionalId,
        UUID procedureId,
        @Size(max = 200) String preference,
        @Size(max = 500) String notes,
        boolean urgent) {}
