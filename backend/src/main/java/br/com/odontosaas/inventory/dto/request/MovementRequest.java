package br.com.odontosaas.inventory.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MovementRequest(
        @NotBlank @Pattern(regexp = "IN|OUT|ADJUST") String type,
        @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal quantity,
        @DecimalMin("0") BigDecimal unitCost,
        @Size(max = 60) String lotNumber,
        LocalDate expiryDate,
        @Size(max = 300) String reason) {}
