package br.com.odontosaas.communication.repository;

import br.com.odontosaas.communication.dto.response.MessageView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepository {

    public record ContactRow(String fullName, String email, boolean allowEmail, String clinic) {}

    public record AppointmentSlotRow(Instant startsAt, String professional, String zone) {}

    public record DueReminderRow(UUID id, UUID patientId) {}

    public record PendingMessageRow(UUID id, String toAddress, String subject, String body, int attempts) {}

    private final JdbcClient jdbc;

    public MessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<MessageView> list(UUID tenantId, String status) {
        return jdbc.sql("""
                        select m.id, m.type, m.to_address, m.subject, m.status, m.attempts, m.last_error,
                               p.full_name as patient_name, m.created_at, m.sent_at
                        from outbound_messages m
                        left join patients p on p.tenant_id = m.tenant_id and p.id = m.patient_id
                        where m.tenant_id = ? and (cast(? as text) is null or m.status = ?)
                        order by m.created_at desc limit 300
                        """)
                .params(tenantId, status, status)
                .query(MessageView.class)
                .list();
    }

    public int requeueFailed(UUID tenantId, UUID messageId) {
        return jdbc.sql("""
                        update outbound_messages set status = 'PENDING', attempts = 0, last_error = null
                        where tenant_id = ? and id = ? and status = 'FAILED'
                        """).params(tenantId, messageId).update();
    }

    public Optional<ContactRow> findContact(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                select p.full_name, p.email, p.allow_email, t.name as clinic from patients p
                join tenants t on t.id = p.tenant_id where p.tenant_id = ? and p.id = ?
                """).params(tenantId, patientId).query(ContactRow.class).optional();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID appointmentId,
            String type,
            String toAddress,
            String subject,
            String body) {
        jdbc.sql("""
                        insert into outbound_messages (id, tenant_id, patient_id, appointment_id, type, to_address,
                            subject, body) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, patientId, appointmentId, type, toAddress, subject, body)
                .update();
    }

    public AppointmentSlotRow appointmentSlot(UUID tenantId, UUID appointmentId) {
        return jdbc.sql("""
                        select a.starts_at, pr.name as professional, t.time_zone as zone from appointments a
                        join professionals pr on pr.tenant_id = a.tenant_id and pr.id = a.professional_id
                        join tenants t on t.id = a.tenant_id where a.tenant_id = ? and a.id = ?
                        """)
                .params(tenantId, appointmentId)
                .query((rs, n) -> new AppointmentSlotRow(
                        rs.getTimestamp("starts_at").toInstant(), rs.getString("professional"), rs.getString("zone")))
                .single();
    }

    public UUID appointmentPatientId(UUID tenantId, UUID appointmentId) {
        return jdbc.sql("select patient_id from appointments where tenant_id = ? and id = ?")
                .params(tenantId, appointmentId)
                .query(UUID.class)
                .single();
    }

    public List<UUID> activeTenantIds() {
        return jdbc.sql("select id from tenants where status = 'ACTIVE'")
                .query(UUID.class)
                .list();
    }

    public List<DueReminderRow> dueReminders(UUID tenantId) {
        return jdbc.sql("""
                select a.id, a.patient_id from appointments a
                left join clinic_settings s on s.tenant_id = a.tenant_id
                where a.tenant_id = ? and coalesce(s.reminders_enabled, true)
                  and a.status in ('SCHEDULED', 'CONFIRMED') and a.reminder_sent_at is null
                  and a.starts_at > now()
                  and a.starts_at <= now() + make_interval(hours => coalesce(s.reminder_hours_before, 24))
                """).param(tenantId).query(DueReminderRow.class).list();
    }

    public List<PendingMessageRow> pending(UUID tenantId) {
        return jdbc.sql("""
                select id, to_address, subject, body, attempts from outbound_messages
                where tenant_id = ? and status = 'PENDING' order by created_at limit 50
                """).param(tenantId).query(PendingMessageRow.class).list();
    }

    public void markSent(UUID tenantId, UUID messageId) {
        jdbc.sql("update outbound_messages set status = 'SENT', sent_at = now(), attempts = attempts + 1,"
                        + " body = regexp_replace(body, '#token=[A-Za-z0-9_-]+', '#token=[removido]', 'g')"
                        + " where tenant_id = ? and id = ?")
                .params(tenantId, messageId)
                .update();
    }

    public void recordFailure(UUID tenantId, UUID messageId, int attempts, String error, String status) {
        jdbc.sql("update outbound_messages set attempts = ?, last_error = ?, status = ?"
                        + " where tenant_id = ? and id = ?")
                .params(attempts, error, status, tenantId, messageId)
                .update();
    }
}
