package br.com.odontosaas.inventory.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 60) String category,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal minStock,
        UUID supplierId,
        Boolean active) {}
