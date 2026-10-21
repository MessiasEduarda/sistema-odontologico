package br.com.odontosaas.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MovementView(
        UUID id,
        String type,
        BigDecimal quantity,
        BigDecimal unitCost,
        String lotNumber,
        LocalDate expiryDate,
        String reason,
        BigDecimal balanceAfter,
        String createdByName,
        Instant createdAt) {}
