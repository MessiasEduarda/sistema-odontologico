package br.com.odontosaas.clinic.repository;

import br.com.odontosaas.clinic.dto.request.ProfessionalRequest;
import br.com.odontosaas.clinic.dto.request.ScheduleSlot;
import br.com.odontosaas.clinic.dto.response.ProfessionalView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProfessionalRepository {

    private static final String PROFESSIONAL_COLUMNS =
            "id, user_id, name, cro, cro_state, specialty, color, phone, email, commission_percent, active, version";

    private final JdbcClient jdbc;

    public ProfessionalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProfessionalView> list(UUID tenantId, Boolean active) {
        return jdbc.sql("select " + PROFESSIONAL_COLUMNS + " from professionals where tenant_id = ?"
                        + (active == null ? "" : " and active = " + (active ? "true" : "false"))
                        + " order by name")
                .param(tenantId)
                .query(ProfessionalView.class)
                .list();
    }

    public Optional<ProfessionalView> find(UUID tenantId, UUID id) {
        return jdbc.sql("select " + PROFESSIONAL_COLUMNS + " from professionals where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(ProfessionalView.class)
                .optional();
    }

    public void insert(UUID id, UUID tenantId, String name, ProfessionalRequest r, boolean active) {
        jdbc.sql("""
                        insert into professionals (id, tenant_id, user_id, name, cro, cro_state, specialty, color,
                            phone, email, commission_percent, active)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.userId(),
                        name,
                        r.cro(),
                        r.croState(),
                        r.specialty(),
                        r.color(),
                        r.phone(),
                        r.email(),
                        r.commissionPercent(),
                        active)
                .update();
    }

    public int update(UUID tenantId, UUID id, String name, ProfessionalRequest r) {
        return jdbc.sql("""
                        update professionals set user_id = ?, name = ?, cro = ?, cro_state = ?, specialty = ?,
                            color = ?, phone = ?, email = ?, commission_percent = ?,
                            active = coalesce(cast(? as boolean), active),
                            updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ? and (cast(? as bigint) is null or version = ?)
                        """)
                .params(
                        r.userId(),
                        name,
                        r.cro(),
                        r.croState(),
                        r.specialty(),
                        r.color(),
                        r.phone(),
                        r.email(),
                        r.commissionPercent(),
                        r.active(),
                        tenantId,
                        id,
                        r.version(),
                        r.version())
                .update();
    }

    public boolean isInactive(UUID tenantId, UUID id) {
        return jdbc.sql("select exists(select 1 from professionals where tenant_id = ? and id = ? and not active)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    public boolean exists(UUID tenantId, UUID id) {
        return exists("professionals", tenantId, id);
    }

    private boolean exists(String table, UUID tenantId, UUID id) {
        return jdbc.sql("select exists(select 1 from " + table + " where tenant_id = ? and id = ?)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    public boolean isMemberUser(UUID tenantId, UUID userId) {
        return jdbc.sql("select exists(select 1 from memberships where tenant_id = ? and user_id = ?)")
                .params(tenantId, userId)
                .query(Boolean.class)
                .single();
    }

    public boolean isLinkedToUser(UUID tenantId, UUID professionalId, UUID userId) {
        return jdbc.sql("select exists(select 1 from professionals where tenant_id = ? and id = ? and user_id = ?)")
                .params(tenantId, professionalId, userId)
                .query(Boolean.class)
                .single();
    }

    public List<ScheduleSlot> schedule(UUID tenantId, UUID professionalId) {
        return jdbc.sql("""
                        select weekday, start_time, end_time from professional_schedules
                        where tenant_id = ? and professional_id = ? order by weekday, start_time
                        """)
                .params(tenantId, professionalId)
                .query(ScheduleSlot.class)
                .list();
    }

    public void deleteSchedule(UUID tenantId, UUID professionalId) {
        jdbc.sql("delete from professional_schedules where tenant_id = ? and professional_id = ?")
                .params(tenantId, professionalId)
                .update();
    }

    public void insertScheduleSlot(UUID id, UUID tenantId, UUID professionalId, ScheduleSlot s) {
        jdbc.sql("""
                        insert into professional_schedules (id, tenant_id, professional_id, weekday, start_time,
                            end_time) values (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, professionalId, s.weekday(), s.startTime(), s.endTime())
                .update();
    }
}
