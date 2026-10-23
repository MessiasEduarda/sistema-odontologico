package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortalLoginBody(
        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254)
        String email,

        @NotBlank(message = "Informe a senha.") @Size(max = 128)
        String password) {}
