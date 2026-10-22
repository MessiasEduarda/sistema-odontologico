package br.com.odontosaas.inventory.repository;

import br.com.odontosaas.inventory.dto.response.ExpiringLot;
import br.com.odontosaas.inventory.dto.response.MovementView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class StockMovementRepository {

    private final JdbcClient jdbc;

    public StockMovementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<MovementView> findByProduct(UUID tenantId, UUID productId) {
        return jdbc.sql("""
                        select m.id, m.type, m.quantity, m.unit_cost, m.lot_number, m.expiry_date, m.reason,
                               m.balance_after, u.name as created_by_name, m.created_at
                        from stock_movements m left join users u on u.id = m.created_by
                        where m.tenant_id = ? and m.product_id = ? order by m.created_at desc limit 500
                        """)
                .params(tenantId, productId)
                .query(MovementView.class)
                .list();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID productId,
            String type,
            BigDecimal quantity,
            BigDecimal unitCost,
            String lotNumber,
            LocalDate expiryDate,
            String reason,
            BigDecimal balanceAfter,
            UUID createdBy) {
        jdbc.sql("""
                        insert into stock_movements (id, tenant_id, product_id, type, quantity, unit_cost, lot_number,
                            expiry_date, reason, balance_after, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        productId,
                        type,
                        quantity,
                        unitCost,
                        lotNumber,
                        expiryDate,
                        reason,
                        balanceAfter,
                        createdBy)
                .update();
    }

    public List<ExpiringLot> findExpiringLots(UUID tenantId) {
        return jdbc.sql("""
                        select distinct m.product_id, p.name as product_name, m.lot_number, m.expiry_date
                        from stock_movements m join products p on p.tenant_id = m.tenant_id and p.id = m.product_id
                        where m.tenant_id = ? and m.type = 'IN' and m.expiry_date is not null
                          and m.expiry_date <= current_date + 60 and p.current_stock > 0 and p.active
                        order by m.expiry_date limit 100
                        """).param(tenantId).query(ExpiringLot.class).list();
    }
}
