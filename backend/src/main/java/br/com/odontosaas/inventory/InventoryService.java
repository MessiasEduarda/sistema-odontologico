package br.com.odontosaas.inventory;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.inventory.InventoryDtos.Alerts;
import br.com.odontosaas.inventory.InventoryDtos.ExpiringLot;
import br.com.odontosaas.inventory.InventoryDtos.MovementRequest;
import br.com.odontosaas.inventory.InventoryDtos.MovementView;
import br.com.odontosaas.inventory.InventoryDtos.ProductRequest;
import br.com.odontosaas.inventory.InventoryDtos.ProductView;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Estoque: produtos e movimentações (somente inclusão). O saldo nunca fica negativo (regra do banco). */
@Service
public class InventoryService {

    private static final String SELECT = """
            select pr.id, pr.name, pr.category, pr.unit, pr.min_stock, pr.current_stock,
                   (pr.current_stock <= pr.min_stock) as low_stock, pr.supplier_id, s.name as supplier_name,
                   pr.active, pr.updated_at
            from products pr left join suppliers s on s.tenant_id = pr.tenant_id and s.id = pr.supplier_id
            """;

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;

    public InventoryService(JdbcClient jdbc, TenantRefs refs, AuditService audit) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProductView> products(String query, boolean onlyLowStock) {
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbc.sql(SELECT
                        + " where pr.tenant_id = ? and (cast(? as text) is null or lower(pr.name) like ?"
                        + " or lower(coalesce(pr.category, '')) like ?)"
                        + (onlyLowStock ? " and pr.active and pr.current_stock <= pr.min_stock" : "")
                        + " order by pr.name")
                .params(TenantContext.requireTenantId(), like, like, like)
                .query(ProductView.class)
                .list();
    }

    @Transactional(readOnly = true)
    public ProductView product(UUID id) {
        return jdbc.sql(SELECT + " where pr.tenant_id = ? and pr.id = ?")
                .params(TenantContext.requireTenantId(), id)
                .query(ProductView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Produto"));
    }

    @Transactional
    public ProductView create(ProductRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into products (id, tenant_id, name, category, unit, min_stock, supplier_id, active)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.name().strip(),
                        blank(r.category()),
                        r.unit().strip(),
                        r.minStock(),
                        r.supplierId(),
                        Flags.activeIfOmitted(r.active()))
                .update();
        audit.record(AuditAction.RECORD_CREATED, "product", id, Map.of());
        return product(id);
    }

    @Transactional
    public ProductView update(UUID id, ProductRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("suppliers", tenantId, r.supplierId(), "Fornecedor");
        int updated = jdbc.sql("""
                        update products set name = ?, category = ?, unit = ?, min_stock = ?, supplier_id = ?,
                            active = coalesce(cast(? as boolean), active), updated_at = now(), version = version + 1 where tenant_id = ? and id = ?
                        """)
                .params(
                        r.name().strip(),
                        blank(r.category()),
                        r.unit().strip(),
                        r.minStock(),
                        r.supplierId(),
                        r.active(),
                        tenantId,
                        id)
                .update();
        Versioned.requireFound(updated, "Produto");
        audit.record(AuditAction.RECORD_UPDATED, "product", id, Map.of());
        return product(id);
    }

    @Transactional(readOnly = true)
    public List<MovementView> movements(UUID productId) {
        product(productId);
        return jdbc.sql("""
                        select m.id, m.type, m.quantity, m.unit_cost, m.lot_number, m.expiry_date, m.reason,
                               m.balance_after, u.name as created_by_name, m.created_at
                        from stock_movements m left join users u on u.id = m.created_by
                        where m.tenant_id = ? and m.product_id = ? order by m.created_at desc limit 500
                        """)
                .params(TenantContext.requireTenantId(), productId)
                .query(MovementView.class)
                .list();
    }

    /** Registra a movimentação e atualiza o saldo na mesma transação (com bloqueio da linha do produto). */
    @Transactional
    public ProductView move(UUID productId, MovementRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal current = jdbc.sql("select current_stock from products where tenant_id = ? and id = ? for update")
                .params(tenantId, productId)
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Produto"));
        BigDecimal delta = switch (r.type()) {
            case "IN" -> r.quantity();
            case "OUT" -> r.quantity().negate();
            default -> r.quantity().subtract(current); // ADJUST: quantity = saldo contado
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
        jdbc.sql("""
                        insert into stock_movements (id, tenant_id, product_id, type, quantity, unit_cost, lot_number,
                            expiry_date, reason, balance_after, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
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
                        TenantContext.userId().orElse(null))
                .update();
        jdbc.sql("""
                        update products set current_stock = ?, updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ?
                        """).params(balance, tenantId, productId).update();
        audit.record(AuditAction.RECORD_CREATED, "stock_movement", id, Map.of("type", r.type()));
        return product(productId);
    }

    @Transactional(readOnly = true)
    public Alerts alerts() {
        UUID tenantId = TenantContext.requireTenantId();
        List<ExpiringLot> expiring =
                jdbc.sql("""
                        select distinct m.product_id, p.name as product_name, m.lot_number, m.expiry_date
                        from stock_movements m join products p on p.tenant_id = m.tenant_id and p.id = m.product_id
                        where m.tenant_id = ? and m.type = 'IN' and m.expiry_date is not null
                          and m.expiry_date <= current_date + 60 and p.current_stock > 0 and p.active
                        order by m.expiry_date limit 100
                        """).param(tenantId).query(ExpiringLot.class).list();
        return new Alerts(products(null, true), expiring);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
