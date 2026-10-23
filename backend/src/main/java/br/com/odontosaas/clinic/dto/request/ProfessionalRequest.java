package br.com.odontosaas.clinic.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalRequest(
        UUID userId,
        @NotBlank @Size(max = 160) String name,

        @NotBlank(message = "Informe o CRO.") @Size(max = 20)
        String cro,

        @NotBlank(message = "Informe a UF do CRO.")
        @Pattern(regexp = "^[A-Z]{2}$", message = "use a sigla do estado (ex.: SP)")
        String croState,

        @Size(max = 80) String specialty,

        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "cor inválida")
        String color,

        @Size(max = 20) String phone,

        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254)
        String email,

        @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent,
        Boolean active,
        Long version) {}
