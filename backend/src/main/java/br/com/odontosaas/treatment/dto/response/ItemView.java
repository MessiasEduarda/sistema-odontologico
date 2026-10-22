package br.com.odontosaas.treatment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemView(
        UUID id,
        UUID procedureId,
        String description,
        Integer tooth,
        String surfaces,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal total,
        String status,
        Instant doneAt) {}
