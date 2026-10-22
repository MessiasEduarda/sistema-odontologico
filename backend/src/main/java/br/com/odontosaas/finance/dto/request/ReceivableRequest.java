package br.com.odontosaas.finance.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableRequest(
        @NotNull UUID patientId,
        @NotBlank @Size(max = 200) String description,

        @NotNull @DecimalMin("0.01") @DecimalMax("9999999") BigDecimal total,

        @Min(1) @Max(48) int installments,
        @NotNull LocalDate firstDueDate) {}
