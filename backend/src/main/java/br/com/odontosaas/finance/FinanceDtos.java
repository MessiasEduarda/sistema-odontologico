package br.com.odontosaas.finance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {}

    static final String METHODS = "CASH|PIX|CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|BOLETO|OTHER";

    public record ReceivableRequest(
            @NotNull UUID patientId,
            @NotBlank @Size(max = 200) String description,

            @NotNull @DecimalMin("0.01") @DecimalMax("9999999")
            BigDecimal total,

            @Min(1) @Max(48) int installments,
            @NotNull LocalDate firstDueDate) {}

    public record ReceivableSummary(
            UUID id,
            UUID patientId,
            String patientName,
            UUID treatmentPlanId,
            String description,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            String status,
            LocalDate nextDueDate,
            int overdueInstallments,
            Instant createdAt) {}

    public record PaymentView(
            UUID id,
            BigDecimal amount,
            BigDecimal refundedAmount,
            String method,
            Instant paidAt,
            String notes,
            String createdByName) {}

    public record InstallmentView(
            UUID id,
            int number,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal paidAmount,
            BigDecimal balance,
            String status,
            boolean overdue,
            List<PaymentView> payments) {}

    public record ReceivableView(
            UUID id,
            UUID patientId,
            String patientName,
            UUID treatmentPlanId,
            String description,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            String status,
            String cancelReason,
            Instant createdAt,
            List<InstallmentView> installments) {}

    public record InstallmentRow(
            UUID id,
            UUID receivableId,
            UUID patientId,
            String patientName,
            String description,
            int number,
            int installmentsCount,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal paidAmount,
            BigDecimal balance,
            String status,
            boolean overdue) {}

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("9999999")
            BigDecimal amount,

            @NotBlank @Pattern(regexp = METHODS) String method,
            Instant paidAt,
            @Size(max = 300) String notes) {}

    public record RefundRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 300) String reason) {}

    public record ReasonRequest(@NotBlank @Size(max = 300) String reason) {}

    // ------------------------------------------------------------------ Contas a pagar e fornecedores

    public record PayableRequest(
            UUID supplierId,
            @NotBlank @Size(max = 200) String description,
            @NotBlank @Size(max = 60) String category,

            @NotNull @DecimalMin("0.01") @DecimalMax("9999999")
            BigDecimal amount,

            @NotNull LocalDate dueDate,
            @Size(max = 500) String notes) {}

    public record PayableView(
            UUID id,
            UUID supplierId,
            String supplierName,
            String description,
            String category,
            BigDecimal amount,
            LocalDate dueDate,
            String status,
            boolean overdue,
            Instant paidAt,
            String paymentMethod,
            String notes) {}

    public record PayRequest(
            @NotBlank @Pattern(regexp = METHODS) String method, Instant paidAt) {}

    public record SupplierRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 18) String document,
            @Size(max = 20) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 500) String notes,
            Boolean active) {}

    public record SupplierView(
            UUID id, String name, String document, String phone, String email, String notes, boolean active) {}

    // ------------------------------------------------------------------ Fluxo de caixa e resumo

    public record CashFlowDay(LocalDate date, BigDecimal inflow, BigDecimal outflow, BigDecimal net) {}

    public record CashFlowView(
            LocalDate from,
            LocalDate to,
            BigDecimal totalInflow,
            BigDecimal totalOutflow,
            BigDecimal net,
            List<CashFlowDay> days,
            List<MethodTotal> byMethod) {}

    public record MethodTotal(String method, BigDecimal amount) {}

    public record FinanceSummary(
            BigDecimal receivedThisMonth,
            BigDecimal paidOutThisMonth,
            BigDecimal openReceivables,
            BigDecimal overdueReceivables,
            int overdueInstallments,
            BigDecimal openPayables,
            BigDecimal payablesDueNext7Days) {}
}
