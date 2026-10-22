package br.com.odontosaas.treatment.repository;

import br.com.odontosaas.treatment.dto.response.ItemView;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TreatmentPlanItemRepository {

    private final JdbcClient jdbc;

    public TreatmentPlanItemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ItemView> findByPlan(UUID tenantId, UUID planId) {
        return jdbc.sql("""
                        select id, procedure_id, description, tooth, surfaces, quantity, unit_price, discount,
                               quantity * unit_price - discount as total, status, done_at
                        from treatment_plan_items where tenant_id = ? and plan_id = ? order by sort_order
                        """).params(tenantId, planId).query(ItemView.class).list();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID planId,
            UUID procedureId,
            String description,
            Integer tooth,
            String surfaces,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discount,
            int sortOrder) {
        jdbc.sql("""
                        insert into treatment_plan_items (id, tenant_id, plan_id, procedure_id, description, tooth,
                            surfaces, quantity, unit_price, discount, sort_order)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        planId,
                        procedureId,
                        description,
                        tooth,
                        surfaces,
                        quantity,
                        unitPrice,
                        discount,
                        sortOrder)
                .update();
    }

    public void deleteByPlan(UUID tenantId, UUID planId) {
        jdbc.sql("delete from treatment_plan_items where tenant_id = ? and plan_id = ?")
                .params(tenantId, planId)
                .update();
    }

    public void updateStatus(String status, boolean voided, UUID tenantId, UUID itemId) {
        jdbc.sql("""
                        update treatment_plan_items set status = ?, done_at = case when ? then null else now() end
                        where tenant_id = ? and id = ?
                        """).params(status, voided, tenantId, itemId).update();
    }

    public UUID findPlanId(UUID tenantId, UUID itemId) {
        return jdbc.sql("select plan_id from treatment_plan_items where tenant_id = ? and id = ?")
                .params(tenantId, itemId)
                .query(UUID.class)
                .single();
    }
}
