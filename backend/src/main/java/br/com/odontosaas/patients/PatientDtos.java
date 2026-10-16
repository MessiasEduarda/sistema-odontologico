package br.com.odontosaas.patients;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PatientDtos {

    private PatientDtos() {}

    public record PatientSummary(
            UUID id,
            String fullName,
            String socialName,
            LocalDate birthDate,
            String cpf,
            String mobile,
            String phone,
            String email,
            boolean active,
            Instant nextAppointment,
            Instant lastAppointment) {}

    public record PatientView(
            UUID id,
            String fullName,
            String socialName,
            LocalDate birthDate,
            String cpf,
            String rg,
            String sex,
            String phone,
            String mobile,
            String email,
            String zipCode,
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state,
            String occupation,
            String referralSource,
            String healthInsurance,
            String insuranceNumber,
            String guardianName,
            String guardianCpf,
            String guardianPhone,
            String notes,
            boolean allowEmail,
            boolean allowSms,
            boolean allowWhatsapp,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    public record PatientRequest(
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 160) String socialName,
            @Past LocalDate birthDate,
            @Size(max = 14) String cpf,
            @Size(max = 20) String rg,

            @Pattern(regexp = "FEMALE|MALE|OTHER|NOT_INFORMED")
            String sex,

            @Size(max = 20) String phone,
            @Size(max = 20) String mobile,
            @Email @Size(max = 254) String email,
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
            boolean allowEmail,
            boolean allowSms,
            boolean allowWhatsapp,
            Long version) {}
}
