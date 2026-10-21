package br.com.odontosaas.platform;

import br.com.odontosaas.platform.SubscriptionService.SubscriptionView;
import br.com.odontosaas.shared.PlanLimits;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Implementa {@link PlanLimits} com o plano da assinatura e a contagem de cadastros ativos da clínica. */
@Service
class PlanLimitsService implements PlanLimits {

    private final SubscriptionService subscriptions;
    private final JdbcClient jdbc;

    PlanLimitsService(SubscriptionService subscriptions, JdbcClient jdbc) {
        this.subscriptions = subscriptions;
        this.jdbc = jdbc;
    }

    @Override
    public int availablePatients(UUID tenantId) {
        return available(
                tenantId, "patients", subscriptions.subscription(tenantId).map(SubscriptionView::maxPatients));
    }

    @Override
    public int availableProfessionals(UUID tenantId) {
        return available(
                tenantId,
                "professionals",
                subscriptions.subscription(tenantId).map(SubscriptionView::maxProfessionals));
    }

    private int available(UUID tenantId, String table, Optional<Integer> limit) {
        if (limit.isEmpty()) return Integer.MAX_VALUE;
        lock(tenantId, table);
        long active = jdbc.sql("select count(*) from " + table + " where tenant_id = ? and active")
                .param(tenantId)
                .query(Long.class)
                .single();
        return (int) Math.max(0, limit.get() - active);
    }

    /** Serializa a checagem do limite por clínica e tabela até o fim da transação (evita estourar em paralelo). */
    private void lock(UUID tenantId, String table) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
        jdbc.sql("select 1 from (select pg_advisory_xact_lock(hashtextextended(?, 0))) l")
                .param(tenantId + ":" + table)
                .query(Integer.class)
                .single();
    }
}
