package br.com.odontosaas.inventory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {}

    public record ProductRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 60) String category,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal minStock,
            UUID supplierId,
            boolean active) {}

    public record ProductView(
            UUID id,
            String name,
            String category,
            String unit,
            BigDecimal minStock,
            BigDecimal currentStock,
            boolean lowStock,
            UUID supplierId,
            String supplierName,
            boolean active,
            Instant updatedAt) {}

    /**
     * IN/OUT: quantity é a quantidade movimentada (positiva). ADJUST: quantity é o saldo contado no inventário (o
     * sistema registra a diferença).
     */
    public record MovementRequest(
            @NotBlank @Pattern(regexp = "IN|OUT|ADJUST") String type,
            @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal quantity,
            @DecimalMin("0") BigDecimal unitCost,
            @Size(max = 60) String lotNumber,
            LocalDate expiryDate,
            @Size(max = 300) String reason) {}

    public record MovementView(
            UUID id,
            String type,
            BigDecimal quantity,
            BigDecimal unitCost,
            String lotNumber,
            LocalDate expiryDate,
            String reason,
            BigDecimal balanceAfter,
            String createdByName,
            Instant createdAt) {}

    public record ExpiringLot(UUID productId, String productName, String lotNumber, LocalDate expiryDate) {}

    public record Alerts(List<ProductView> lowStock, List<ExpiringLot> expiring) {}
}
