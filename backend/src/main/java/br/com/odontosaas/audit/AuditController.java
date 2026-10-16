package br.com.odontosaas.audit;

import br.com.odontosaas.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trilha de auditoria da clínica ativa (somente leitura). */
@RestController
@Tag(name = "Auditoria")
public class AuditController {

    public record AuditEventView(
            UUID id,
            String action,
            String actorName,
            String actorEmail,
            String resourceType,
            String resourceId,
            String ip,
            String metadata,
            Instant occurredAt) {}

    private final JdbcClient jdbc;

    public AuditController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/clinic/audit")
    @PreAuthorize("hasAuthority('audit:read')")
    @Transactional(readOnly = true)
    @Operation(summary = "Eventos da clínica", description = "Filtros: from, to (datas), action, resourceType")
    public List<AuditEventView> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType) {
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

    private static java.sql.Timestamp ts(Instant i) {
        return i == null ? null : java.sql.Timestamp.from(i);
    }
}
