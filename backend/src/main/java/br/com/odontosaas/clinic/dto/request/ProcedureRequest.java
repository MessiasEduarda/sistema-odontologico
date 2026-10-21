package br.com.odontosaas.clinic.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProcedureRequest(
        @Size(max = 20) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 60) String category,
        @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal defaultPrice,
        @Min(5) @Max(480) int durationMinutes,
        Boolean active) {}
