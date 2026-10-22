package br.com.odontosaas.treatment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanRequest(
        @NotNull UUID patientId,
        @NotNull UUID professionalId,
        @NotBlank @Size(max = 160) String title,
        @DecimalMin("0") BigDecimal discount,
        @Size(max = 500) String paymentConditions,
        @Size(max = 4000) String notes,
        LocalDate validUntil,
        @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items,
        Long version) {}
