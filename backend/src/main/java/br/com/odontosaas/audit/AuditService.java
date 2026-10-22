package br.com.odontosaas.audit;

import br.com.odontosaas.audit.dto.response.AuditEventView;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AuditService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(AuditAction action, String resourceType, Object resourceId, Map<String, ?> metadata) {
        TenantContext.State state = TenantContext.current();
        insert(state.tenantId(), state.userId(), action, resourceType, resourceId, metadata);
    }

    public void recordGlobal(
            AuditAction action, UUID actorUserId, String resourceType, Object resourceId, Map<String, ?> metadata) {
        insert(null, actorUserId, action, resourceType, resourceId, metadata);
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> events(LocalDate from, LocalDate to, String action, String resourceType) {
        UUID tenantId = TenantContext.requireTenantId();
        ZoneId zone = ZoneId.of(jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single());
        Instant start = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return jdbc.sql("""
                        select e.id, e.action, u.name as actor_name, u.email as actor_email, e.resource_type,
                               e.resource_id, e.ip, e.metadata::text as metadata, e.occurred_at
                        from audit_events e left join users u on u.id = e.actor_user_id
                        where e.tenant_id = ?
                          and (cast(? as timestamptz) is null or e.occurred_at >= ?)
                          and (cast(? as timestamptz) is null or e.occurred_at < ?)
                          and (cast(? as text) is null or e.action = ?)
                          and (cast(? as text) is null or e.resource_type = ?)
                        order by e.occurred_at desc limit 500
                        """)
                .params(tenantId, ts(start), ts(start), ts(end), ts(end), action, action, resourceType, resourceType)
                .query(AuditEventView.class)
                .list();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private void insert(
            UUID tenantId,
            UUID actorUserId,
            AuditAction action,
            String resourceType,
            Object resourceId,
            Map<String, ?> metadata) {
        jdbc.sql("""
                        insert into audit_events
                            (id, tenant_id, actor_user_id, action, resource_type, resource_id, ip, metadata)
                        values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                        """)
                .params(
                        Ids.newId(),
                        tenantId,
                        actorUserId,
                        action.name(),
                        resourceType,
                        resourceId == null ? null : resourceId.toString(),
                        RequestInfo.clientIp(),
                        metadata == null || metadata.isEmpty() ? null : json.writeValueAsString(metadata))
                .update();
    }
}
