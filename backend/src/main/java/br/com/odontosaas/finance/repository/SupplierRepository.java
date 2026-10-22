package br.com.odontosaas.finance.repository;

import br.com.odontosaas.finance.dto.response.SupplierView;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SupplierRepository {

    private final JdbcClient jdbc;

    public SupplierRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<SupplierView> list(UUID tenantId) {
        return jdbc.sql("select id, name, document, phone, email, notes, active from suppliers where tenant_id = ?"
                        + " order by name")
                .param(tenantId)
                .query(SupplierView.class)
                .list();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            String name,
            String document,
            String phone,
            String email,
            String notes,
            boolean active) {
        jdbc.sql("""
                        insert into suppliers (id, tenant_id, name, document, phone, email, notes, active)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, name, document, phone, email, notes, active)
                .update();
    }

    public int update(
            String name,
            String document,
            String phone,
            String email,
            String notes,
            Boolean active,
            UUID tenantId,
            UUID id) {
        return jdbc.sql("""
                        update suppliers set name = ?, document = ?, phone = ?, email = ?, notes = ?,
                            active = coalesce(cast(? as boolean), active)
                        where tenant_id = ? and id = ?
                        """)
                .params(name, document, phone, email, notes, active, tenantId, id)
                .update();
    }

    public boolean isActive(UUID tenantId, UUID id) {
        return jdbc.sql("select active from suppliers where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }
}
