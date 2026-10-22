package br.com.odontosaas.clinic.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProcedureView(
        UUID id,
        String code,
        String name,
        String category,
        BigDecimal defaultPrice,
        int durationMinutes,
        boolean active,
        Instant updatedAt) {}
