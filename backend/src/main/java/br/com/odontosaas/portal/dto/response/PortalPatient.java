package br.com.odontosaas.portal.dto.response;

import java.time.LocalDate;

public record PortalPatient(
        String fullName,
        String socialName,
        String cpf,
        LocalDate birthDate,
        String mobile,
        String phone,
        String guardianName,
        String guardianCpf) {}
