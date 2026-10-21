package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Entrega de orientação ao paciente: vira documento do prontuário e, se pedido, é enviada por e-mail. */
public record CareIssueRequest(
        @NotNull UUID professionalId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 20000) String content,
        boolean sendEmail) {}
