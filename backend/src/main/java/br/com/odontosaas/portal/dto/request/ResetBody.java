package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetBody(
        @NotBlank @Size(max = 64) String token,

        @NotBlank(message = "Crie uma senha.") @Size(max = 128)
        String password) {}
