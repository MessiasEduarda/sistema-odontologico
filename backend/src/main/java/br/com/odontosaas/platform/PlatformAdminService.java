package br.com.odontosaas.platform;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visão da administração da plataforma. Usa apenas tabelas globais (clínicas, assinaturas, planos, usuários,
 * sessões) e a auditoria de plataforma — nunca dados de pacientes.
 */
@Service
public class PlatformAdminService {

    public record TenantRow(
            UUID id,
            String name,
            String slug,
            String status,
            Instant createdAt,
            String planCode,
            String planName,
            String subscriptionStatus,
            LocalDate trialEndsAt,
            BigDecimal priceMonthly) {}

    public record Metrics(
            long tenants,
            long activeTenants,
            long suspendedTenants,
            long trialing,
            long payingSubscriptions,
            long pastDue,
            BigDecimal monthlyRecurringRevenue,
            long users,
            long activeSessions24h) {}

    public record PlatformEvent(
            UUID id,
            String action,
            String actorEmail,
            String resourceType,
            String resourceId,
            String ip,
            Instant occurredAt) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public PlatformAdminService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TenantRow> tenants(String query) {
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbc.sql("""
                        select t.id, t.name, t.slug, t.status, t.created_at, p.code as plan_code, p.name as plan_name,
                               s.status as subscription_status, s.trial_ends_at, p.price_monthly
                        from tenants t
                        left join subscriptions s on s.tenant_id = t.id
                        left join plans p on p.id = s.plan_id
                        where cast(? as text) is null or lower(t.name) like ? or lower(t.slug) like ?
                        order by t.name limit 500
                        """).params(like, like, like).query(TenantRow.class).list();
    }

    @Transactional
    public void setTenantStatus(UUID tenantId, String status) {
        int updated = jdbc.sql("update tenants set status = ?, updated_at = now() where id = ?")
                .params(status, tenantId)
                .update();
        if (updated == 0) throw ApiException.notFound("Clínica");
        audit.recordGlobal(
                AuditAction.TENANT_STATUS_CHANGED,
                TenantContext.userId().orElse(null),
                "tenant",
                tenantId,
                Map.of("status", status));
    }

    @Transactional(readOnly = true)
    public Metrics metrics() {
        return jdbc.sql("""
                        select (select count(*) from tenants) as tenants,
                               (select count(*) from tenants where status = 'ACTIVE') as active_tenants,
                               (select count(*) from tenants where status = 'SUSPENDED') as suspended_tenants,
                               (select count(*) from subscriptions where status = 'TRIALING') as trialing,
                               (select count(*) from subscriptions where status = 'ACTIVE') as paying_subscriptions,
                               (select count(*) from subscriptions where status = 'PAST_DUE') as past_due,
                               (select coalesce(sum(p.price_monthly), 0) from subscriptions s
                                   join plans p on p.id = s.plan_id where s.status in ('ACTIVE', 'PAST_DUE'))
                                   as monthly_recurring_revenue,
                               (select count(*) from users where status = 'ACTIVE') as users,
                               (select count(distinct user_id) from sessions where revoked_at is null
                                   and last_seen_at > now() - interval '24 hours') as active_sessions24h
                        """).query(Metrics.class).single();
    }

    /** Histórico de ações de plataforma e autenticação (RLS: apenas eventos sem clínica). */
    @Transactional(readOnly = true)
    public List<PlatformEvent> events(String action) {
        return jdbc.sql("""
                        select e.id, e.action, u.email as actor_email, e.resource_type, e.resource_id, e.ip,
                               e.occurred_at
                        from audit_events e left join users u on u.id = e.actor_user_id
                        where e.tenant_id is null and (cast(? as text) is null or e.action = ?)
                        order by e.occurred_at desc limit 300
                        """).params(action, action).query(PlatformEvent.class).list();
    }
}
