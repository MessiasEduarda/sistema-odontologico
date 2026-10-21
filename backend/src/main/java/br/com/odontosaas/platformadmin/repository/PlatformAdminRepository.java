package br.com.odontosaas.platformadmin.repository;

import br.com.odontosaas.platformadmin.dto.response.Metrics;
import br.com.odontosaas.platformadmin.dto.response.PlatformEvent;
import br.com.odontosaas.platformadmin.dto.response.TenantRow;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas da administração da plataforma sobre tabelas globais (clínicas, assinaturas, planos, usuários, sessões). */
@Repository
public class PlatformAdminRepository {

    private final JdbcClient jdbc;

    public PlatformAdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Clínicas com plano e assinatura; {@code like} nulo não filtra (padrão já em minúsculas, com {@code %}). */
    public List<TenantRow> tenants(String like) {
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

    /** Altera o status da clínica; devolve quantas linhas foram alteradas (0 se não existe). */
    public int updateTenantStatus(UUID tenantId, String status) {
        return jdbc.sql("update tenants set status = ?, updated_at = now() where id = ?")
                .params(status, tenantId)
                .update();
    }

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

    /** Eventos de auditoria sem clínica (RLS), opcionalmente filtrados por ação. */
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
