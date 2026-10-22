package br.com.odontosaas.patients.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
