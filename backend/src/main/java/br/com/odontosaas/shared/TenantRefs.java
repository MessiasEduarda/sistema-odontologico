package br.com.odontosaas.shared;

import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class TenantRefs {

    private static final Set<String> TABLES = Set.of(
            "patients",
            "professionals",
            "rooms",
            "procedures",
            "appointments",
            "treatment_plans",
            "treatment_plan_items",
            "suppliers",
            "products",
            "consent_templates",
            "clinical_notes",
            "installments",
            "payments");

    private final JdbcClient jdbc;

    public TenantRefs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(String table, UUID tenantId, UUID id) {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Tabela não permitida: " + table);
        return jdbc.sql("select exists(select 1 from " + table + " where tenant_id = ? and id = ?)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    public void require(String table, UUID tenantId, UUID id, String label) {
        if (id != null && !exists(table, tenantId, id)) throw ApiException.notFound(label);
    }
}
