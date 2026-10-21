package br.com.odontosaas.scheduling.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.scheduling.dto.request.AppointmentRequest;
import br.com.odontosaas.scheduling.dto.response.AppointmentDetail;
import br.com.odontosaas.scheduling.dto.response.AppointmentEventView;
import br.com.odontosaas.scheduling.dto.response.AppointmentView;
import br.com.odontosaas.scheduling.dto.response.FreeSlot;
import br.com.odontosaas.scheduling.event.AppointmentChanged;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AppointmentService {

    static final String SELECT = """
            select a.id, a.patient_id, p.full_name as patient_name, p.mobile as patient_mobile,
                   a.professional_id, pr.name as professional_name, pr.color as professional_color,
                   a.room_id, r.name as room_name, a.procedure_id, pc.name as procedure_name,
                   a.starts_at, a.ends_at, a.status, a.notes, a.cancellation_reason, a.confirmed_at,
                   a.reminder_sent_at, a.version
            from appointments a
            join patients p on p.tenant_id = a.tenant_id and p.id = a.patient_id
            join professionals pr on pr.tenant_id = a.tenant_id and pr.id = a.professional_id
            left join rooms r on r.tenant_id = a.tenant_id and r.id = a.room_id
            left join procedures pc on pc.tenant_id = a.tenant_id and pc.id = a.procedure_id
            """;

    /** Transições permitidas de status (máquina de estados do atendimento). */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "SCHEDULED", Set.of("CONFIRMED", "ARRIVED", "CANCELLED", "NO_SHOW"),
            "CONFIRMED", Set.of("SCHEDULED", "ARRIVED", "CANCELLED", "NO_SHOW"),
            "ARRIVED", Set.of("IN_PROGRESS", "COMPLETED", "NO_SHOW", "CONFIRMED"),
            "IN_PROGRESS", Set.of("COMPLETED", "ARRIVED"),
            "NO_SHOW", Set.of("SCHEDULED"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of());

    private static final Duration MAX_DURATION = Duration.ofHours(8);

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final ScheduleBlockService blocks;

    public AppointmentService(
            JdbcClient jdbc,
            TenantRefs refs,
            AuditService audit,
            ApplicationEventPublisher events,
            JsonMapper json,
            ScheduleBlockService blocks) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
        this.events = events;
        this.json = json;
        this.blocks = blocks;
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> list(Instant from, Instant to, UUID professionalId, UUID patientId, String status) {
        UUID tenantId = TenantContext.requireTenantId();
        if (patientId == null && (from == null || to == null)) {
            throw ApiException.badRequest("PERIOD_REQUIRED", "Informe o período (from e to) ou o paciente.");
        }
        if (from != null && to != null && Duration.between(from, to).toDays() > 62) {
            throw ApiException.badRequest("PERIOD_TOO_LONG", "O período máximo de consulta é de 62 dias.");
        }
        StringBuilder sql = new StringBuilder(SELECT).append(" where a.tenant_id = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (from != null) {
            sql.append(" and a.ends_at > ?");
            params.add(java.sql.Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and a.starts_at < ?");
            params.add(java.sql.Timestamp.from(to));
        }
        if (professionalId != null) {
            sql.append(" and a.professional_id = ?");
            params.add(professionalId);
        }
        if (patientId != null) {
            sql.append(" and a.patient_id = ?");
            params.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and a.status = ?");
            params.add(status);
        }
        sql.append(
                patientId != null && from == null ? " order by a.starts_at desc limit 200" : " order by a.starts_at");
        return jdbc.sql(sql.toString())
                .params(params)
                .query(AppointmentView.class)
                .list();
    }

    @Transactional(readOnly = true)
    public AppointmentView get(UUID id) {
        return jdbc.sql(SELECT + " where a.tenant_id = ? and a.id = ?")
                .params(TenantContext.requireTenantId(), id)
                .query(AppointmentView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Agendamento"));
    }

    @Transactional(readOnly = true)
    public AppointmentDetail detail(UUID id) {
        AppointmentView appointment = get(id);
        List<AppointmentEventView> history = jdbc.sql("""
                        select e.action, e.details::text as details, u.name as actor_name, e.occurred_at
                        from appointment_events e left join users u on u.id = e.actor_user_id
                        where e.tenant_id = ? and e.appointment_id = ? order by e.occurred_at
                        """)
                .params(TenantContext.requireTenantId(), id)
                .query((rs, n) -> new AppointmentEventView(
                        rs.getString("action"),
                        parse(rs.getString("details")),
                        rs.getString("actor_name"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
        return new AppointmentDetail(appointment, history);
    }

    @Transactional
    public AppointmentView create(AppointmentRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        validate(tenantId, r);
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into appointments (id, tenant_id, patient_id, professional_id, room_id, procedure_id,
                            starts_at, ends_at, notes, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.patientId(),
                        r.professionalId(),
                        r.roomId(),
                        r.procedureId(),
                        java.sql.Timestamp.from(r.startsAt()),
                        java.sql.Timestamp.from(r.endsAt()),
                        blank(r.notes()),
                        TenantContext.userId().orElse(null))
                .update();
        event(
                tenantId,
                id,
                "CREATED",
                Map.of("startsAt", r.startsAt().toString(), "endsAt", r.endsAt().toString()));
        audit.record(AuditAction.RECORD_CREATED, "appointment", id, Map.of());
        events.publishEvent(new AppointmentChanged(tenantId, id, AppointmentChanged.Kind.CREATED));
        return get(id);
    }

    /** Edita/remarca. Atendimentos concluídos ou cancelados não podem ser alterados. */
    @Transactional
    public AppointmentView update(UUID id, AppointmentRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        AppointmentView current = get(id);
        if (Set.of("COMPLETED", "CANCELLED").contains(current.status())) {
            throw ApiException.businessRule(
                    "APPOINTMENT_CLOSED", "Atendimentos concluídos ou cancelados não podem ser alterados.");
        }
        validate(tenantId, r);
        boolean rescheduled = !current.startsAt().equals(r.startsAt())
                || !current.endsAt().equals(r.endsAt())
                || !current.professionalId().equals(r.professionalId());
        int updated = jdbc.sql("""
                        update appointments set patient_id = ?, professional_id = ?, room_id = ?, procedure_id = ?,
                            starts_at = ?, ends_at = ?, notes = ?, updated_at = now(), version = version + 1,
                            reminder_sent_at = case when ? then null else reminder_sent_at end,
                            status = case when ? and status = 'CONFIRMED' then 'SCHEDULED' else status end,
                            confirmed_at = case when ? then null else confirmed_at end
                        where tenant_id = ? and id = ? and (cast(? as bigint) is null or version = ?)
                        """)
                .params(
                        r.patientId(),
                        r.professionalId(),
                        r.roomId(),
                        r.procedureId(),
                        java.sql.Timestamp.from(r.startsAt()),
                        java.sql.Timestamp.from(r.endsAt()),
                        blank(r.notes()),
                        rescheduled,
                        rescheduled,
                        rescheduled,
                        tenantId,
                        id,
                        r.version(),
                        r.version())
                .update();
        Versioned.check(updated, true, "Agendamento");
        Map<String, Object> details = new LinkedHashMap<>();
        if (rescheduled) {
            details.put("fromStartsAt", current.startsAt().toString());
            details.put("toStartsAt", r.startsAt().toString());
            details.put("fromProfessional", current.professionalName());
        }
        event(tenantId, id, rescheduled ? "RESCHEDULED" : "UPDATED", details);
        audit.record(AuditAction.RECORD_UPDATED, "appointment", id, Map.of());
        if (rescheduled) {
            events.publishEvent(new AppointmentChanged(tenantId, id, AppointmentChanged.Kind.RESCHEDULED));
        }
        return get(id);
    }

    @Transactional
    public AppointmentView changeStatus(UUID id, String status, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        AppointmentView current = get(id);
        applyStatus(tenantId, current, status, reason, TenantContext.userId().orElse(null));
        return get(id);
    }

    /** Aplica a transição de status (usada também pela confirmação/cancelamento feita pelo paciente). */
    void applyStatus(UUID tenantId, AppointmentView current, String status, String reason, UUID actor) {
        if (current.status().equals(status)) return;
        if (!TRANSITIONS.getOrDefault(current.status(), Set.of()).contains(status)) {
            throw ApiException.businessRule(
                    "INVALID_STATUS_TRANSITION",
                    "Não é possível mudar de " + label(current.status()) + " para " + label(status) + ".");
        }
        if ("CANCELLED".equals(status) && (reason == null || reason.isBlank())) {
            throw ApiException.invalidField("reason", "Informe o motivo do cancelamento.");
        }
        int updated = jdbc.sql("""
                        update appointments set status = ?, updated_at = now(), version = version + 1,
                            cancellation_reason = case when ? = 'CANCELLED' then ? else cancellation_reason end,
                            confirmed_at = case when ? = 'CONFIRMED' then now() else confirmed_at end
                        where tenant_id = ? and id = ? and version = ?
                        """)
                .params(status, status, blank(reason), status, tenantId, current.id(), current.version())
                .update();
        Versioned.check(updated, true, "Agendamento");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", current.status());
        details.put("to", status);
        if (reason != null && !reason.isBlank()) details.put("reason", reason.strip());
        insertEvent(tenantId, current.id(), "STATUS_CHANGED", details, actor);
        audit.record(
                "CANCELLED".equals(status) ? AuditAction.RECORD_CANCELLED : AuditAction.RECORD_UPDATED,
                "appointment",
                current.id(),
                Map.of("status", status));
        if ("CANCELLED".equals(status)) {
            events.publishEvent(new AppointmentChanged(tenantId, current.id(), AppointmentChanged.Kind.CANCELLED));
        }
    }

    /**
     * Resposta do PACIENTE (link ou portal): confirmar ou cancelar uma consulta futura ainda não iniciada. Exige o
     * contexto da clínica dona do agendamento (já validado pelo chamador por token).
     */
    @Transactional
    public AppointmentView respondAsPatient(UUID appointmentId, String action) {
        UUID tenantId = TenantContext.requireTenantId();
        AppointmentView a = get(appointmentId);
        if (!canPatientRespond(a)) {
            throw ApiException.businessRule(
                    "CANNOT_RESPOND", "Esta consulta não pode mais ser confirmada ou cancelada por aqui.");
        }
        if ("CONFIRM".equals(action)) {
            applyStatus(tenantId, a, "CONFIRMED", null, null);
        } else {
            applyStatus(tenantId, a, "CANCELLED", "Cancelado pelo paciente", null);
        }
        return get(appointmentId);
    }

    public static boolean canPatientRespond(AppointmentView a) {
        return Set.of("SCHEDULED", "CONFIRMED").contains(a.status())
                && a.startsAt().isAfter(Instant.now());
    }

    /**
     * Horários livres do profissional num dia, considerando a grade semanal dele e os atendimentos já marcados.
     */
    @Transactional(readOnly = true)
    public List<FreeSlot> availability(UUID professionalId, LocalDate date, int durationMinutes) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("professionals", tenantId, professionalId, "Profissional");
        ZoneId zone = ZoneId.of(jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single());
        record Window(LocalTime startTime, LocalTime endTime) {}
        List<Window> windows = jdbc.sql("""
                        select start_time, end_time from professional_schedules
                        where tenant_id = ? and professional_id = ? and weekday = ? order by start_time
                        """)
                .params(tenantId, professionalId, date.getDayOfWeek().getValue())
                .query(Window.class)
                .list();
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        record Busy(Instant startsAt, Instant endsAt) {}
        List<Busy> busy = new ArrayList<>(jdbc.sql("""
                        select starts_at, ends_at from appointments
                        where tenant_id = ? and professional_id = ? and status not in ('CANCELLED', 'NO_SHOW')
                          and starts_at < ? and ends_at > ?
                        """)
                .params(tenantId, professionalId, java.sql.Timestamp.from(dayEnd), java.sql.Timestamp.from(dayStart))
                .query((rs, n) -> new Busy(
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant()))
                .list());
        for (Instant[] period : blocks.blockedPeriods(tenantId, professionalId, dayStart, dayEnd)) {
            busy.add(new Busy(period[0], period[1]));
        }

        Duration step = Duration.ofMinutes(durationMinutes);
        Instant now = Instant.now();
        List<FreeSlot> free = new ArrayList<>();
        for (Window w : windows) {
            ZonedDateTime cursor = date.atTime(w.startTime()).atZone(zone);
            ZonedDateTime end = date.atTime(w.endTime()).atZone(zone);
            while (!cursor.plus(step).isAfter(end)) {
                Instant s = cursor.toInstant();
                Instant e = cursor.plus(step).toInstant();
                boolean overlaps = busy.stream().anyMatch(b -> b.startsAt().isBefore(e) && s.isBefore(b.endsAt()));
                if (!overlaps && s.isAfter(now)) free.add(new FreeSlot(s, e));
                cursor = cursor.plus(step);
            }
        }
        return free;
    }

    private void validate(UUID tenantId, AppointmentRequest r) {
        if (!r.endsAt().isAfter(r.startsAt())) {
            throw ApiException.invalidField("endsAt", "O término deve ser depois do início.");
        }
        if (Duration.between(r.startsAt(), r.endsAt()).compareTo(MAX_DURATION) > 0) {
            throw ApiException.invalidField("endsAt", "Um atendimento pode durar no máximo 8 horas.");
        }
        refs.require("patients", tenantId, r.patientId(), "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        refs.require("rooms", tenantId, r.roomId(), "Sala");
        refs.require("procedures", tenantId, r.procedureId(), "Procedimento");
        blocks.blockingReason(tenantId, r.professionalId(), r.startsAt(), r.endsAt())
                .ifPresent(reason -> {
                    throw ApiException.businessRule(
                            "SCHEDULE_BLOCKED", "Horário bloqueado na agenda (" + reason + "). Escolha outro horário.");
                });
    }

    private void event(UUID tenantId, UUID appointmentId, String action, Map<String, ?> details) {
        insertEvent(
                tenantId, appointmentId, action, details, TenantContext.userId().orElse(null));
    }

    private void insertEvent(UUID tenantId, UUID appointmentId, String action, Map<String, ?> details, UUID actor) {
        jdbc.sql("""
                        insert into appointment_events (id, tenant_id, appointment_id, action, details, actor_user_id)
                        values (?, ?, ?, ?, cast(? as jsonb), ?)
                        """)
                .params(Ids.newId(), tenantId, appointmentId, action, json.writeValueAsString(details), actor)
                .update();
    }

    private Map<String, Object> parse(String value) {
        if (value == null) return Map.of();
        return json.readValue(value, new TypeReference<Map<String, Object>>() {});
    }

    static String label(String status) {
        return switch (status) {
            case "SCHEDULED" -> "Agendado";
            case "CONFIRMED" -> "Confirmado";
            case "ARRIVED" -> "Paciente chegou";
            case "IN_PROGRESS" -> "Em atendimento";
            case "COMPLETED" -> "Concluído";
            case "NO_SHOW" -> "Faltou";
            case "CANCELLED" -> "Cancelado";
            default -> status;
        };
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
