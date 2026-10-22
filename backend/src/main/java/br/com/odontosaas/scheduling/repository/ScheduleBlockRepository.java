package br.com.odontosaas.scheduling.repository;

import br.com.odontosaas.scheduling.dto.response.ScheduleBlockView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleBlockRepository {

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

    public ScheduleBlockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScheduleBlockView> list(UUID tenantId, Instant from, Instant to) {
        return jdbc.sql(SELECT + " where b.tenant_id = ? and b.ends_at > ? and b.starts_at < ? order by b.starts_at")
                .params(tenantId, Timestamp.from(from), Timestamp.from(to))
                .query(ScheduleBlockView.class)
                .list();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID professionalId,
            Instant startsAt,
            Instant endsAt,
            String reason,
            UUID createdBy) {
        jdbc.sql("""
                        insert into schedule_blocks (id, tenant_id, professional_id, starts_at, ends_at, reason, created_by)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        professionalId,
                        Timestamp.from(startsAt),
                        Timestamp.from(endsAt),
                        reason,
                        createdBy)
                .update();
    }

    public int delete(UUID tenantId, UUID id) {
        return jdbc.sql("delete from schedule_blocks where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .update();
    }

    public Optional<String> blockingReason(UUID tenantId, UUID professionalId, Instant startsAt, Instant endsAt) {
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

    public List<Instant[]> blockedPeriods(UUID tenantId, UUID professionalId, Instant from, Instant to) {
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

    public ScheduleBlockView get(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where b.tenant_id = ? and b.id = ?")
                .params(tenantId, id)
                .query(ScheduleBlockView.class)
                .single();
    }
}
