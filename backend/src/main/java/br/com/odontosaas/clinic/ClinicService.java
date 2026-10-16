package br.com.odontosaas.clinic;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinic.ClinicDtos.ProcedureRequest;
import br.com.odontosaas.clinic.ClinicDtos.ProcedureView;
import br.com.odontosaas.clinic.ClinicDtos.ProfessionalRequest;
import br.com.odontosaas.clinic.ClinicDtos.ProfessionalView;
import br.com.odontosaas.clinic.ClinicDtos.RoomRequest;
import br.com.odontosaas.clinic.ClinicDtos.RoomView;
import br.com.odontosaas.clinic.ClinicDtos.ScheduleSlot;
import br.com.odontosaas.clinic.ClinicDtos.SettingsRequest;
import br.com.odontosaas.clinic.ClinicDtos.SettingsView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastros da clínica ativa. Todas as consultas filtram por tenant_id (do contexto do servidor) e também passam
 * pelo RLS.
 */
@Service
public class ClinicService {

    private final JdbcClient jdbc;
    private final AuditService audit;

    public ClinicService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static UUID tenant() {
        return TenantContext.requireTenantId();
    }

    // ------------------------------------------------------------------ Configurações

    @Transactional(readOnly = true)
    public SettingsView settings() {
        return jdbc.sql("""
                        select t.name, s.legal_name, s.document, s.phone, s.email, s.zip_code, s.address, s.city,
                               s.state,
                               coalesce(s.default_appointment_minutes, 30) as default_appointment_minutes,
                               coalesce(s.reminders_enabled, true) as reminders_enabled,
                               coalesce(s.reminder_hours_before, 24) as reminder_hours_before,
                               t.time_zone,
                               coalesce(s.aesthetics_enabled, false) as aesthetics_enabled,
                               l.updated_at as logo_updated_at
                        from tenants t left join clinic_settings s on s.tenant_id = t.id
                        left join clinic_logos l on l.tenant_id = t.id
                        where t.id = ?
                        """).param(tenant()).query(SettingsView.class).single();
    }

    @Transactional
    public SettingsView updateSettings(SettingsRequest r) {
        UUID tenantId = tenant();
        jdbc.sql("update tenants set name = ?, updated_at = now() where id = ?")
                .params(r.name().strip(), tenantId)
                .update();
        jdbc.sql("""
                        insert into clinic_settings (tenant_id, legal_name, document, phone, email, zip_code, address,
                            city, state, default_appointment_minutes, reminders_enabled, reminder_hours_before,
                            aesthetics_enabled)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (tenant_id) do update set
                            legal_name = excluded.legal_name, document = excluded.document, phone = excluded.phone,
                            email = excluded.email, zip_code = excluded.zip_code, address = excluded.address,
                            city = excluded.city, state = excluded.state,
                            default_appointment_minutes = excluded.default_appointment_minutes,
                            reminders_enabled = excluded.reminders_enabled,
                            reminder_hours_before = excluded.reminder_hours_before,
                            aesthetics_enabled = excluded.aesthetics_enabled, updated_at = now()
                        """)
                .params(
                        tenantId,
                        r.legalName(),
                        r.document(),
                        r.phone(),
                        r.email(),
                        r.zipCode(),
                        r.address(),
                        r.city(),
                        r.state(),
                        r.defaultAppointmentMinutes(),
                        r.remindersEnabled(),
                        r.reminderHoursBefore(),
                        r.aestheticsEnabled())
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "clinic_settings", tenantId, Map.of());
        return settings();
    }

    // ------------------------------------------------------------------ Profissionais

    private static final String PROFESSIONAL_COLUMNS =
            "id, user_id, name, cro, cro_state, specialty, color, phone, email, commission_percent, active, version";

    @Transactional(readOnly = true)
    public List<ProfessionalView> professionals(Boolean active) {
        return jdbc.sql("select " + PROFESSIONAL_COLUMNS + " from professionals where tenant_id = ?"
                        + (active == null ? "" : " and active = " + (active ? "true" : "false"))
                        + " order by name")
                .param(tenant())
                .query(ProfessionalView.class)
                .list();
    }

    @Transactional(readOnly = true)
    public ProfessionalView professional(UUID id) {
        return jdbc.sql("select " + PROFESSIONAL_COLUMNS + " from professionals where tenant_id = ? and id = ?")
                .params(tenant(), id)
                .query(ProfessionalView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Profissional"));
    }

    @Transactional
    public ProfessionalView createProfessional(ProfessionalRequest r) {
        UUID tenantId = tenant();
        requireMemberUser(tenantId, r.userId());
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into professionals (id, tenant_id, user_id, name, cro, cro_state, specialty, color,
                            phone, email, commission_percent, active)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.userId(),
                        r.name().strip(),
                        r.cro(),
                        r.croState(),
                        r.specialty(),
                        r.color(),
                        r.phone(),
                        r.email(),
                        r.commissionPercent(),
                        r.active())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "professional", id, Map.of());
        return professional(id);
    }

    @Transactional
    public ProfessionalView updateProfessional(UUID id, ProfessionalRequest r) {
        UUID tenantId = tenant();
        requireMemberUser(tenantId, r.userId());
        int updated = jdbc.sql("""
                        update professionals set user_id = ?, name = ?, cro = ?, cro_state = ?, specialty = ?,
                            color = ?, phone = ?, email = ?, commission_percent = ?, active = ?,
                            updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ? and (cast(? as bigint) is null or version = ?)
                        """)
                .params(
                        r.userId(),
                        r.name().strip(),
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
        Versioned.check(updated, exists("professionals", tenantId, id), "Profissional");
        audit.record(AuditAction.RECORD_UPDATED, "professional", id, Map.of());
        return professional(id);
    }

    @Transactional(readOnly = true)
    public List<ScheduleSlot> schedule(UUID professionalId) {
        professional(professionalId);
        return jdbc.sql("""
                        select weekday, start_time, end_time from professional_schedules
                        where tenant_id = ? and professional_id = ? order by weekday, start_time
                        """)
                .params(tenant(), professionalId)
                .query(ScheduleSlot.class)
                .list();
    }

    /** Substitui a grade semanal do profissional. Intervalos do mesmo dia não podem se sobrepor. */
    @Transactional
    public List<ScheduleSlot> replaceSchedule(UUID professionalId, List<ScheduleSlot> slots) {
        UUID tenantId = tenant();
        professional(professionalId);
        for (ScheduleSlot s : slots) {
            if (!s.startTime().isBefore(s.endTime())) {
                throw ApiException.invalidField("slots", "O início deve ser antes do fim em todos os horários.");
            }
            boolean overlaps = slots.stream()
                    .anyMatch(o -> o != s
                            && o.weekday() == s.weekday()
                            && o.startTime().isBefore(s.endTime())
                            && s.startTime().isBefore(o.endTime()));
            if (overlaps) throw ApiException.invalidField("slots", "Há horários sobrepostos no mesmo dia.");
        }
        jdbc.sql("delete from professional_schedules where tenant_id = ? and professional_id = ?")
                .params(tenantId, professionalId)
                .update();
        for (ScheduleSlot s : slots) {
            jdbc.sql("""
                            insert into professional_schedules (id, tenant_id, professional_id, weekday, start_time,
                                end_time) values (?, ?, ?, ?, ?, ?)
                            """)
                    .params(Ids.newId(), tenantId, professionalId, s.weekday(), s.startTime(), s.endTime())
                    .update();
        }
        audit.record(AuditAction.RECORD_UPDATED, "professional_schedule", professionalId, Map.of());
        return schedule(professionalId);
    }

    private void requireMemberUser(UUID tenantId, UUID userId) {
        if (userId == null) return;
        boolean member = jdbc.sql("select exists(select 1 from memberships where tenant_id = ? and user_id = ?)")
                .params(tenantId, userId)
                .query(Boolean.class)
                .single();
        if (!member) throw ApiException.invalidField("userId", "Usuário não faz parte da equipe desta clínica.");
    }

    // ------------------------------------------------------------------ Salas

    @Transactional(readOnly = true)
    public List<RoomView> rooms() {
        return jdbc.sql("select id, name, active from rooms where tenant_id = ? order by name")
                .param(tenant())
                .query(RoomView.class)
                .list();
    }

    @Transactional
    public RoomView createRoom(RoomRequest r) {
        UUID id = Ids.newId();
        jdbc.sql("insert into rooms (id, tenant_id, name, active) values (?, ?, ?, ?)")
                .params(id, tenant(), r.name().strip(), r.active())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "room", id, Map.of());
        return new RoomView(id, r.name().strip(), r.active());
    }

    @Transactional
    public RoomView updateRoom(UUID id, RoomRequest r) {
        int updated = jdbc.sql("update rooms set name = ?, active = ? where tenant_id = ? and id = ?")
                .params(r.name().strip(), r.active(), tenant(), id)
                .update();
        Versioned.requireFound(updated, "Sala");
        audit.record(AuditAction.RECORD_UPDATED, "room", id, Map.of());
        return new RoomView(id, r.name().strip(), r.active());
    }

    // ------------------------------------------------------------------ Procedimentos

    private static final String PROCEDURE_COLUMNS =
            "id, code, name, category, default_price, duration_minutes, active, updated_at";

    @Transactional(readOnly = true)
    public List<ProcedureView> procedures(String query, Boolean active) {
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return jdbc.sql("select " + PROCEDURE_COLUMNS + " from procedures where tenant_id = ?"
                        + " and (cast(? as text) is null or lower(name) like ? or lower(coalesce(code, '')) like ?)"
                        + " and (cast(? as boolean) is null or active = ?) order by name")
                .params(tenant(), like, like, like, active, active)
                .query(ProcedureView.class)
                .list();
    }

    @Transactional(readOnly = true)
    public ProcedureView procedure(UUID id) {
        return jdbc.sql("select " + PROCEDURE_COLUMNS + " from procedures where tenant_id = ? and id = ?")
                .params(tenant(), id)
                .query(ProcedureView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Procedimento"));
    }

    @Transactional
    public ProcedureView createProcedure(ProcedureRequest r) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into procedures (id, tenant_id, code, name, category, default_price, duration_minutes,
                            active) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenant(),
                        blankToNull(r.code()),
                        r.name().strip(),
                        blankToNull(r.category()),
                        r.defaultPrice(),
                        r.durationMinutes(),
                        r.active())
                .update();
        audit.record(AuditAction.RECORD_CREATED, "procedure", id, Map.of());
        return procedure(id);
    }

    @Transactional
    public ProcedureView updateProcedure(UUID id, ProcedureRequest r) {
        int updated = jdbc.sql("""
                        update procedures set code = ?, name = ?, category = ?, default_price = ?,
                            duration_minutes = ?, active = ?, updated_at = now()
                        where tenant_id = ? and id = ?
                        """)
                .params(
                        blankToNull(r.code()),
                        r.name().strip(),
                        blankToNull(r.category()),
                        r.defaultPrice(),
                        r.durationMinutes(),
                        r.active(),
                        tenant(),
                        id)
                .update();
        Versioned.requireFound(updated, "Procedimento");
        audit.record(
                AuditAction.RECORD_UPDATED,
                "procedure",
                id,
                Map.of("price", r.defaultPrice().toPlainString()));
        return procedure(id);
    }

    private boolean exists(String table, UUID tenantId, UUID id) {
        return jdbc.sql("select exists(select 1 from " + table + " where tenant_id = ? and id = ?)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
