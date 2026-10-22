package br.com.odontosaas.patients.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
