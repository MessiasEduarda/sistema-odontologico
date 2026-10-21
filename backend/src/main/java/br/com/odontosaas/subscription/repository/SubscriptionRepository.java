package br.com.odontosaas.subscription.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados das assinaturas das clínicas ({@code subscriptions}), junto com os dados do plano. */
@Repository
public class SubscriptionRepository {

    /** Assinatura com os dados do plano, sem o cálculo de cobrança (feito pelo serviço). */
    public record SubscriptionRow(
            UUID tenantId,
            String planCode,
            String planName,
            BigDecimal priceMonthly,
            String status,
            LocalDate trialEndsAt,
            LocalDate currentPeriodEnd,
            List<String> features,
            Integer maxProfessionals,
            Integer maxPatients,
            String notes,
            Instant updatedAt,
            String paymentUrl) {}

    private final JdbcClient jdbc;

    public SubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cria a assinatura de avaliação (30 dias) se a clínica ainda não tiver uma. */
    public void insertTrial(UUID tenantId, UUID planId) {
        jdbc.sql("""
                        insert into subscriptions (tenant_id, plan_id, status, trial_ends_at)
                        values (?, ?, 'TRIALING', current_date + 30) on conflict (tenant_id) do nothing
                        """).params(tenantId, planId).update();
    }

    public Optional<SubscriptionRow> find(UUID tenantId) {
        return jdbc.sql("""
                        select s.tenant_id, p.code as plan_code, p.name as plan_name, p.price_monthly, s.status,
                               s.trial_ends_at, s.current_period_end, p.features, p.max_professionals, p.max_patients,
                               s.notes, s.updated_at, s.payment_url
                        from subscriptions s join plans p on p.id = s.plan_id where s.tenant_id = ?
                        """)
                .param(tenantId)
                .query((rs, n) -> new SubscriptionRow(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("plan_code"),
                        rs.getString("plan_name"),
                        rs.getBigDecimal("price_monthly"),
                        rs.getString("status"),
                        rs.getObject("trial_ends_at", LocalDate.class),
                        rs.getObject("current_period_end", LocalDate.class),
                        PlanRepository.features(rs.getArray("features")),
                        (Integer) rs.getObject("max_professionals"),
                        (Integer) rs.getObject("max_patients"),
                        rs.getString("notes"),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getString("payment_url")))
                .optional();
    }

    /** Cria ou substitui a assinatura da clínica. */
    public void upsert(
            UUID tenantId,
            UUID planId,
            String status,
            LocalDate trialEndsAt,
            LocalDate currentPeriodEnd,
            String notes,
            String paymentUrl) {
        jdbc.sql("""
                        insert into subscriptions (tenant_id, plan_id, status, trial_ends_at, current_period_end, notes,
                            payment_url)
                        values (?, ?, ?, ?, ?, ?, ?)
                        on conflict (tenant_id) do update set plan_id = excluded.plan_id, status = excluded.status,
                            trial_ends_at = excluded.trial_ends_at, current_period_end = excluded.current_period_end,
                            notes = excluded.notes, payment_url = excluded.payment_url, updated_at = now()
                        """)
                .params(tenantId, planId, status, trialEndsAt, currentPeriodEnd, notes, paymentUrl)
                .update();
    }
}
