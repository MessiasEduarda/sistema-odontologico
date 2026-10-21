package br.com.odontosaas.inventory.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductView(
        UUID id,
        String name,
        String category,
        String unit,
        BigDecimal minStock,
        BigDecimal currentStock,
        boolean lowStock,
        UUID supplierId,
        String supplierName,
        boolean active,
        Instant updatedAt) {}
