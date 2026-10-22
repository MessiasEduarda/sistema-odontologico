package br.com.odontosaas.inventory.repository;

import br.com.odontosaas.inventory.dto.response.ProductView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private static final String SELECT = """
            select pr.id, pr.name, pr.category, pr.unit, pr.min_stock, pr.current_stock,
                   (pr.current_stock <= pr.min_stock) as low_stock, pr.supplier_id, s.name as supplier_name,
                   pr.active, pr.updated_at
            from products pr left join suppliers s on s.tenant_id = pr.tenant_id and s.id = pr.supplier_id
            """;

    private final JdbcClient jdbc;

    public ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProductView> list(UUID tenantId, String like, boolean onlyLowStock) {
        return jdbc.sql(SELECT
                        + " where pr.tenant_id = ? and (cast(? as text) is null or lower(pr.name) like ?"
                        + " or lower(coalesce(pr.category, '')) like ?)"
                        + (onlyLowStock ? " and pr.active and pr.current_stock <= pr.min_stock" : "")
                        + " order by pr.name")
                .params(tenantId, like, like, like)
                .query(ProductView.class)
                .list();
    }

    public Optional<ProductView> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where pr.tenant_id = ? and pr.id = ?")
                .params(tenantId, id)
                .query(ProductView.class)
                .optional();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            String name,
            String category,
            String unit,
            BigDecimal minStock,
            UUID supplierId,
            boolean active) {
        jdbc.sql("""
                        insert into products (id, tenant_id, name, category, unit, min_stock, supplier_id, active)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, name, category, unit, minStock, supplierId, active)
                .update();
    }

    public int update(
            String name,
            String category,
            String unit,
            BigDecimal minStock,
            UUID supplierId,
            Boolean active,
            UUID tenantId,
            UUID id) {
        return jdbc.sql("""
                        update products set name = ?, category = ?, unit = ?, min_stock = ?, supplier_id = ?,
                            active = coalesce(cast(? as boolean), active), updated_at = now(), version = version + 1 where tenant_id = ? and id = ?
                        """)
                .params(name, category, unit, minStock, supplierId, active, tenantId, id)
                .update();
    }

    public Optional<BigDecimal> findStockForUpdate(UUID tenantId, UUID productId) {
        return jdbc.sql("select current_stock from products where tenant_id = ? and id = ? for update")
                .params(tenantId, productId)
                .query(BigDecimal.class)
                .optional();
    }

    public void updateStock(BigDecimal balance, UUID tenantId, UUID productId) {
        jdbc.sql("""
                        update products set current_stock = ?, updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ?
                        """).params(balance, tenantId, productId).update();
    }
}
