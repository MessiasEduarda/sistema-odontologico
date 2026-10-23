package br.com.odontosaas.portal.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ActivationBody(
        @NotBlank @Size(max = 64) String token,
        @NotNull(message = "Informe a data de nascimento.") LocalDate birthDate,

        @NotBlank(message = "Crie uma senha.") @Size(max = 128)
        String password,

        @AssertTrue(message = "É necessário aceitar os Termos de Uso e a Política de Privacidade.")
        boolean acceptedTerms) {}
