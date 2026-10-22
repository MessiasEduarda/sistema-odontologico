package br.com.odontosaas.subscription.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record PlanRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9_]{2,40}$") String code,
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0") BigDecimal priceMonthly,
        @Min(1) Integer maxProfessionals,
        @Min(1) Integer maxPatients,
        @NotNull List<String> features,
        @NotNull Boolean active) {}
