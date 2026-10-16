package br.com.odontosaas.treatment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TreatmentDtos {

    private TreatmentDtos() {}

    public record ItemRequest(
            UUID procedureId,
            @NotBlank @Size(max = 200) String description,
            @Min(11) @Max(85) Integer tooth,
            @Pattern(regexp = "^[MODVLPI]{1,6}$") String surfaces,
            @Min(1) @Max(100) int quantity,
            @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal unitPrice,
            @DecimalMin("0") BigDecimal discount) {}

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

    public record ItemView(
            UUID id,
            UUID procedureId,
            String description,
            Integer tooth,
            String surfaces,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            BigDecimal total,
            String status,
            Instant doneAt) {}

    public record PlanSummary(
            UUID id,
            UUID patientId,
            String patientName,
            UUID professionalId,
            String professionalName,
            String title,
            String status,
            BigDecimal total,
            int itemsTotal,
            int itemsDone,
            Instant createdAt,
            Instant approvedAt) {}

    public record PlanView(
            UUID id,
            UUID patientId,
            String patientName,
            UUID professionalId,
            String professionalName,
            String title,
            String status,
            BigDecimal subtotal,
            BigDecimal itemsDiscount,
            BigDecimal discount,
            BigDecimal total,
            String paymentConditions,
            String notes,
            LocalDate validUntil,
            Instant approvedAt,
            String approvedByName,
            Instant createdAt,
            long version,
            UUID receivableId,
            List<ItemView> items) {}

    public record ApproveRequest(
            @NotBlank @Size(max = 160) String approvedByName,
            boolean generateReceivable,
            @Min(1) @Max(48) int installments,
            LocalDate firstDueDate) {}

    public record ReasonRequest(@NotBlank @Size(max = 300) String reason) {}
}
