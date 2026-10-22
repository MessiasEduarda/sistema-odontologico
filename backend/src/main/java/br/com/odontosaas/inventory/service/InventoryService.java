package br.com.odontosaas.inventory.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.inventory.dto.request.MovementRequest;
import br.com.odontosaas.inventory.dto.request.ProductRequest;
import br.com.odontosaas.inventory.dto.response.Alerts;
import br.com.odontosaas.inventory.dto.response.ExpiringLot;
import br.com.odontosaas.inventory.dto.response.MovementView;
import br.com.odontosaas.inventory.dto.response.ProductView;
import br.com.odontosaas.inventory.repository.ProductRepository;
import br.com.odontosaas.inventory.repository.StockMovementRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Flags;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ProductRepository productRepo;
    private final StockMovementRepository movementRepo;
    private final TenantRefs refs;
    private final AuditService audit;

    public InventoryService(
            ProductRepository productRepo, StockMovementRepository movementRepo, TenantRefs refs, AuditService audit) {
        this.productRepo = productRepo;
        this.movementRepo = movementRepo;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProductView> products(String query, boolean onlyLowStock) {
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return productRepo.list(TenantContext.requireTenantId(), like, onlyLowStock);
    }

    @Transactional(readOnly = true)
    public ProductView product(UUID id) {
        return productRepo
                .find(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> ApiException.notFound("Produto"));
    }

    @Transactional
    public ProductView create(ProductRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        UUID id = Ids.newId();
        productRepo.insert(
                id,
                tenantId,
                r.name().strip(),
                blank(r.category()),
                r.unit().strip(),
                r.minStock(),
                r.supplierId(),
                Flags.activeIfOmitted(r.active()));
        audit.record(AuditAction.RECORD_CREATED, "product", id, Map.of());
        return product(id);
    }

    @Transactional
    public ProductView update(UUID id, ProductRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        int updated = productRepo.update(
                r.name().strip(),
                blank(r.category()),
                r.unit().strip(),
                r.minStock(),
                r.supplierId(),
                r.active(),
                tenantId,
                id);
        Versioned.requireFound(updated, "Produto");
        audit.record(AuditAction.RECORD_UPDATED, "product", id, Map.of());
        return product(id);
    }

    @Transactional(readOnly = true)
    public List<MovementView> movements(UUID productId) {
        product(productId);
        return movementRepo.findByProduct(TenantContext.requireTenantId(), productId);
    }

    @Transactional
    public ProductView move(UUID productId, MovementRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal current =
                productRepo.findStockForUpdate(tenantId, productId).orElseThrow(() -> ApiException.notFound("Produto"));
        BigDecimal delta = switch (r.type()) {
            case "IN" -> r.quantity();
            case "OUT" -> r.quantity().negate();
            default -> r.quantity().subtract(current);
        };
        if (delta.signum() == 0) {
            throw ApiException.invalidField("quantity", "A movimentação não altera o saldo.");
        }
        if (!"ADJUST".equals(r.type()) && r.quantity().signum() <= 0) {
            throw ApiException.invalidField("quantity", "Informe uma quantidade maior que zero.");
        }
        if ("ADJUST".equals(r.type()) && (r.reason() == null || r.reason().isBlank())) {
            throw ApiException.invalidField("reason", "Informe o motivo do ajuste.");
        }
        BigDecimal balance = current.add(delta);
        if (balance.signum() < 0) {
            throw ApiException.businessRule(
                    "INSUFFICIENT_STOCK",
                    "Saldo insuficiente: há " + current.stripTrailingZeros().toPlainString() + ".");
        }
        UUID id = Ids.newId();
        movementRepo.insert(
                id,
                tenantId,
                productId,
                r.type(),
                delta,
                r.unitCost(),
                blank(r.lotNumber()),
                r.expiryDate(),
                blank(r.reason()),
                balance,
                TenantContext.userId().orElse(null));
        productRepo.updateStock(balance, tenantId, productId);
        audit.record(AuditAction.RECORD_CREATED, "stock_movement", id, Map.of("type", r.type()));
        return product(productId);
    }

    @Transactional(readOnly = true)
    public Alerts alerts() {
        UUID tenantId = TenantContext.requireTenantId();
        List<ExpiringLot> expiring = movementRepo.findExpiringLots(tenantId);
        return new Alerts(products(null, true), expiring);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
