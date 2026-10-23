package br.com.odontosaas.patients.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PatientRequest(
        @NotBlank(message = "Informe o nome completo.") @Size(max = 160)
        String fullName,

        @Size(max = 160) String socialName,

        @NotNull(message = "Informe a data de nascimento.")
        @Past(message = "A data de nascimento não pode ser no futuro.")
        LocalDate birthDate,

        @NotBlank(message = "Informe o CPF.") @Size(max = 14)
        String cpf,

        @Size(max = 20) String rg,

        @Pattern(regexp = "FEMALE|MALE|OTHER|NOT_INFORMED") String sex,

        @Size(max = 20) String phone,

        @NotBlank(message = "Informe o celular.") @Size(max = 20)
        String mobile,

        @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254)
        String email,

        @Size(max = 9) String zipCode,
        @Size(max = 160) String street,
        @Size(max = 20) String number,
        @Size(max = 80) String complement,
        @Size(max = 80) String district,
        @Size(max = 100) String city,

        @Pattern(regexp = "^[A-Z]{2}$", message = "use a sigla do estado (ex.: SP)")
        String state,

        @Size(max = 100) String occupation,
        @Size(max = 100) String referralSource,
        @Size(max = 100) String healthInsurance,
        @Size(max = 50) String insuranceNumber,
        @Size(max = 160) String guardianName,
        @Size(max = 14) String guardianCpf,
        @Size(max = 20) String guardianPhone,
        @Size(max = 4000) String notes,
        Boolean allowEmail,
        Boolean allowSms,
        Boolean allowWhatsapp,
        Long version) {}
