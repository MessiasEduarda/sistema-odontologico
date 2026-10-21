package br.com.odontosaas.clinic.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalView(
        UUID id,
        UUID userId,
        String name,
        String cro,
        String croState,
        String specialty,
        String color,
        String phone,
        String email,
        BigDecimal commissionPercent,
        boolean active,
        long version) {}
