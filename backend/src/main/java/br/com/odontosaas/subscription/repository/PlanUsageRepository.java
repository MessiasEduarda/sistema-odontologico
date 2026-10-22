package br.com.odontosaas.subscription.repository;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlanUsageRepository {

    private final JdbcClient jdbc;

    public PlanUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long countActiveProfessionals(UUID tenantId) {
        return jdbc.sql("select count(*) from professionals where tenant_id = ? and active")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    public long countActivePatients(UUID tenantId) {
        return jdbc.sql("select count(*) from patients where tenant_id = ? and active")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    public long countActive(UUID tenantId, String table) {
        return jdbc.sql("select count(*) from " + table + " where tenant_id = ? and active")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    public void lockAdvisory(UUID tenantId, String table) {
        jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtextextended(?, 0))) l")
                .param(tenantId + ":" + table)
                .query(Integer.class)
                .single();
    }
}
