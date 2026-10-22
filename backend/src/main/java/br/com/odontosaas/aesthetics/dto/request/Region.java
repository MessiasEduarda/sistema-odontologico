package br.com.odontosaas.aesthetics.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record Region(
        @NotBlank @Size(max = 80) String region,
        @NotNull @DecimalMin(value = "0.01") BigDecimal quantity,
        @Size(max = 300) String notes) {}
