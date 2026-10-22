package br.com.odontosaas.subscription.service;

import br.com.odontosaas.shared.PlanLimits;
import br.com.odontosaas.subscription.dto.response.SubscriptionView;
import br.com.odontosaas.subscription.repository.PlanUsageRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class PlanLimitsService implements PlanLimits {

    private final SubscriptionService subscriptions;
    private final PlanUsageRepository usage;

    PlanLimitsService(SubscriptionService subscriptions, PlanUsageRepository usage) {
        this.subscriptions = subscriptions;
        this.usage = usage;
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
        long active = usage.countActive(tenantId, table);
        return (int) Math.max(0, limit.get() - active);
    }

    private void lock(UUID tenantId, String table) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
        usage.lockAdvisory(tenantId, table);
    }
}
