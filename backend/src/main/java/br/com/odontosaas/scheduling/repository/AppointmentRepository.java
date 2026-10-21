package br.com.odontosaas.scheduling.repository;

import br.com.odontosaas.scheduling.dto.request.AppointmentRequest;
import br.com.odontosaas.scheduling.dto.response.AppointmentView;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso a dados dos agendamentos: consulta e gravação, histórico (eventos), horários de atendimento e ocupação do
 * profissional, além do token do link de confirmação enviado ao paciente.
 */
@Repository
public class AppointmentRepository {

    private static final String SELECT = """
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

    /** Evento do histórico do agendamento; {@code details} é o JSON em texto. */
    public record EventRow(String action, String details, String actorName, Instant occurredAt) {}

    /** Faixa de atendimento do profissional num dia da semana. */
    public record ScheduleWindowRow(LocalTime startTime, LocalTime endTime) {}

    /** Período já ocupado por um agendamento. */
    public record BusyRow(Instant startsAt, Instant endsAt) {}

    /** Clínica e agendamento resolvidos a partir do hash do token de confirmação. */
    public record TokenTarget(UUID tenantId, UUID appointmentId) {}

    private final JdbcClient jdbc;

    public AppointmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lista com filtros opcionais. Sem {@code from} e com paciente, ordena do mais recente para o mais antigo e limita
     * a 200 registros.
     */
    public List<AppointmentView> list(
            UUID tenantId, Instant from, Instant to, UUID professionalId, UUID patientId, String status) {
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

    public Optional<AppointmentView> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where a.tenant_id = ? and a.id = ?")
                .params(tenantId, id)
                .query(AppointmentView.class)
                .optional();
    }

    public List<EventRow> history(UUID tenantId, UUID appointmentId) {
        return jdbc.sql("""
                        select e.action, e.details::text as details, u.name as actor_name, e.occurred_at
                        from appointment_events e left join users u on u.id = e.actor_user_id
                        where e.tenant_id = ? and e.appointment_id = ? order by e.occurred_at
                        """)
                .params(tenantId, appointmentId)
                .query((rs, n) -> new EventRow(
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getString("actor_name"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    public void insert(UUID id, UUID tenantId, AppointmentRequest r, String notes, UUID createdBy) {
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
                        notes,
                        createdBy)
                .update();
    }

    /** Edita/remarca com controle otimista de versão; devolve as linhas afetadas. */
    public int update(UUID tenantId, UUID id, AppointmentRequest r, String notes, boolean rescheduled) {
        return jdbc.sql("""
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
                        notes,
                        rescheduled,
                        rescheduled,
                        rescheduled,
                        tenantId,
                        id,
                        r.version(),
                        r.version())
                .update();
    }

    /** Muda o status exigindo a versão lida; devolve as linhas afetadas. */
    public int updateStatus(UUID tenantId, UUID id, long version, String status, String reason) {
        return jdbc.sql("""
                        update appointments set status = ?, updated_at = now(), version = version + 1,
                            cancellation_reason = case when ? = 'CANCELLED' then ? else cancellation_reason end,
                            confirmed_at = case when ? = 'CONFIRMED' then now() else confirmed_at end
                        where tenant_id = ? and id = ? and version = ?
                        """)
                .params(status, status, reason, status, tenantId, id, version)
                .update();
    }

    public void insertEvent(UUID id, UUID tenantId, UUID appointmentId, String action, String detailsJson, UUID actor) {
        jdbc.sql("""
                        insert into appointment_events (id, tenant_id, appointment_id, action, details, actor_user_id)
                        values (?, ?, ?, ?, cast(? as jsonb), ?)
                        """)
                .params(id, tenantId, appointmentId, action, detailsJson, actor)
                .update();
    }

    /** Fuso horário da clínica. */
    public String clinicTimeZone(UUID tenantId) {
        return jdbc.sql("select time_zone from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    /** Nome da clínica. */
    public String clinicName(UUID tenantId) {
        return jdbc.sql("select name from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }

    /** Faixas semanais de atendimento do profissional num dia da semana (1 = segunda ... 7 = domingo). */
    public List<ScheduleWindowRow> scheduleWindows(UUID tenantId, UUID professionalId, int weekday) {
        return jdbc.sql("""
                        select start_time, end_time from professional_schedules
                        where tenant_id = ? and professional_id = ? and weekday = ? order by start_time
                        """)
                .params(tenantId, professionalId, weekday)
                .query(ScheduleWindowRow.class)
                .list();
    }

    /** Agendamentos ativos do profissional que encostam no intervalo informado. */
    public List<BusyRow> busyPeriods(UUID tenantId, UUID professionalId, Instant dayStart, Instant dayEnd) {
        return jdbc.sql("""
                        select starts_at, ends_at from appointments
                        where tenant_id = ? and professional_id = ? and status not in ('CANCELLED', 'NO_SHOW')
                          and starts_at < ? and ends_at > ?
                        """)
                .params(tenantId, professionalId, java.sql.Timestamp.from(dayEnd), java.sql.Timestamp.from(dayStart))
                .query((rs, n) -> new BusyRow(
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant()))
                .list();
    }

    /** Grava o hash do token do link de confirmação do agendamento. */
    public void updateConfirmationTokenHash(UUID tenantId, UUID appointmentId, String tokenHash) {
        jdbc.sql("update appointments set confirmation_token_hash = ? where tenant_id = ? and id = ?")
                .params(tokenHash, tenantId, appointmentId)
                .update();
    }

    public void markReminderSent(UUID tenantId, UUID appointmentId) {
        jdbc.sql("update appointments set reminder_sent_at = now() where tenant_id = ? and id = ?")
                .params(tenantId, appointmentId)
                .update();
    }

    /** Resolve clínica e agendamento pelo hash do token (o RLS libera somente o agendamento com aquele hash). */
    public Optional<TokenTarget> findByTokenHash(String tokenHash) {
        return jdbc.sql("select tenant_id, id as appointment_id from appointments where confirmation_token_hash = ?")
                .param(tokenHash)
                .query(TokenTarget.class)
                .optional();
    }
}
