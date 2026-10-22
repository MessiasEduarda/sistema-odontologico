package br.com.odontosaas.finance.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayableRequest(
        UUID supplierId,
        @NotBlank @Size(max = 200) String description,
        @NotBlank @Size(max = 60) String category,

        @NotNull @DecimalMin("0.01") @DecimalMax("9999999") BigDecimal amount,

        @NotNull LocalDate dueDate,
        @Size(max = 500) String notes) {}
