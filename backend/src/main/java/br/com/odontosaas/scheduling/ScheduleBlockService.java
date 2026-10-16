package br.com.odontosaas.scheduling;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.scheduling.SchedulingDtos.ScheduleBlockRequest;
import br.com.odontosaas.scheduling.SchedulingDtos.ScheduleBlockView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bloqueios de agenda: períodos em que não se marca atendimento (de um profissional ou da clínica inteira). */
@Service
public class ScheduleBlockService {

    private static final Duration MAX_BLOCK = Duration.ofDays(92);

    private static final String SELECT = """
            select b.id, b.professional_id, pr.name as professional_name, b.starts_at, b.ends_at, b.reason,
                   (select count(*) from appointments a
                     where a.tenant_id = b.tenant_id and a.status not in ('CANCELLED', 'NO_SHOW', 'COMPLETED')
                       and a.starts_at < b.ends_at and a.ends_at > b.starts_at
                       and (b.professional_id is null or a.professional_id = b.professional_id)) as appointments_inside
            from schedule_blocks b
            left join professionals pr on pr.tenant_id = b.tenant_id and pr.id = b.professional_id
            """;

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;

    public ScheduleBlockService(JdbcClient jdbc, TenantRefs refs, AuditService audit) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ScheduleBlockView> list(Instant from, Instant to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("PERIOD_REQUIRED", "Informe o período (from e to).");
        }
        return jdbc.sql(SELECT + " where b.tenant_id = ? and b.ends_at > ? and b.starts_at < ? order by b.starts_at")
                .params(TenantContext.requireTenantId(), Timestamp.from(from), Timestamp.from(to))
                .query(ScheduleBlockView.class)
                .list();
    }

    @Transactional
    public ScheduleBlockView create(ScheduleBlockRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!r.endsAt().isAfter(r.startsAt())) {
            throw ApiException.invalidField("endsAt", "O fim deve ser depois do início.");
        }
        if (Duration.between(r.startsAt(), r.endsAt()).compareTo(MAX_BLOCK) > 0) {
            throw ApiException.invalidField("endsAt", "Um bloqueio pode durar no máximo 92 dias.");
        }
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into schedule_blocks (id, tenant_id, professional_id, starts_at, ends_at, reason, created_by)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.professionalId(),
                        Timestamp.from(r.startsAt()),
                        Timestamp.from(r.endsAt()),
                        r.reason().strip(),
                        TenantContext.userId().orElse(null))
                .update();
        audit.record(AuditAction.RECORD_CREATED, "schedule-block", id, Map.of());
        return get(tenantId, id);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        int deleted = jdbc.sql("delete from schedule_blocks where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .update();
        if (deleted == 0) throw ApiException.notFound("Bloqueio");
        audit.record(AuditAction.RECORD_DEACTIVATED, "schedule-block", id, Map.of());
    }

    /** Primeiro bloqueio que cobre o período para o profissional (ou para a clínica inteira), se houver. */
    Optional<String> blockingReason(UUID tenantId, UUID professionalId, Instant startsAt, Instant endsAt) {
        return jdbc.sql("""
                        select reason from schedule_blocks
                        where tenant_id = ? and (professional_id is null or professional_id = ?)
                          and starts_at < ? and ends_at > ?
                        order by starts_at limit 1
                        """)
                .params(tenantId, professionalId, Timestamp.from(endsAt), Timestamp.from(startsAt))
                .query(String.class)
                .optional();
    }

    /** Períodos bloqueados de um profissional num intervalo (inclui os da clínica inteira). */
    List<Instant[]> blockedPeriods(UUID tenantId, UUID professionalId, Instant from, Instant to) {
        return jdbc.sql("""
                        select starts_at, ends_at from schedule_blocks
                        where tenant_id = ? and (professional_id is null or professional_id = ?)
                          and starts_at < ? and ends_at > ?
                        """)
                .params(tenantId, professionalId, Timestamp.from(to), Timestamp.from(from))
                .query((rs, n) -> new Instant[] {
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at").toInstant()
                })
                .list();
    }

    private ScheduleBlockView get(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where b.tenant_id = ? and b.id = ?")
                .params(tenantId, id)
                .query(ScheduleBlockView.class)
                .single();
    }
}
