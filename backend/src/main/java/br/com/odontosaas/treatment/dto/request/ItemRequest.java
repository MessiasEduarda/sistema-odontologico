package br.com.odontosaas.treatment.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemRequest(
        UUID procedureId,
        @NotBlank @Size(max = 200) String description,
        @Min(11) @Max(85) Integer tooth,
        @Pattern(regexp = "^[MODVLPI]{1,6}$") String surfaces,
        @Min(1) @Max(100) int quantity,
        @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal unitPrice,
        @DecimalMin("0") BigDecimal discount) {}
