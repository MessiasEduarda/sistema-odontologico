package br.com.odontosaas.subscription.repository;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de uso dos limites do plano: contagem de cadastros ativos por clínica e trava de serialização. */
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

    /** Cadastros ativos da clínica na tabela informada (nome fixo definido pelo chamador, nunca entrada externa). */
    public long countActive(UUID tenantId, String table) {
        return jdbc.sql("select count(*) from " + table + " where tenant_id = ? and active")
                .param(tenantId)
                .query(Long.class)
                .single();
    }

    /** Trava consultiva até o fim da transação, por clínica e tabela. */
    public void lockAdvisory(UUID tenantId, String table) {
        jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtextextended(?, 0))) l")
                .param(tenantId + ":" + table)
                .query(Integer.class)
                .single();
    }
}
