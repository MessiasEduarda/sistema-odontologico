package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DocumentRequest(
        @NotNull UUID professionalId,

        @NotBlank @Pattern(regexp = "PRESCRIPTION|CERTIFICATE|REFERRAL|INSTRUCTIONS|OTHER")
        String type,

        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 20000) String content,
        Boolean sharedWithPatient) {}
