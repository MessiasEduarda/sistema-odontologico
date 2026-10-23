package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record VerifyCodeBody(
        @NotNull UUID challengeId,

        @NotBlank(message = "Informe o código.") @Pattern(regexp = "^[0-9]{6}$", message = "O código tem 6 números.")
        String code) {}
