package br.com.odontosaas.helpdesk.repository;

import br.com.odontosaas.helpdesk.dto.response.CannedResponseView;
import br.com.odontosaas.helpdesk.dto.response.PlatformTicketSummary;
import br.com.odontosaas.helpdesk.dto.response.TicketAttachmentView;
import br.com.odontosaas.helpdesk.dto.response.TicketMessageView;
import br.com.odontosaas.helpdesk.dto.response.TicketSummary;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SupportRepository {

    private static final String OVERDUE = "(t.first_response_at is null and t.status in ('OPEN', 'IN_PROGRESS')"
            + " and t.first_response_due_at < now())";

    private static final String TICKET = """
            select t.id, t.tenant_id, t.protocol, t.opened_by, u.name as opened_by_name, u.email as opened_by_email,
                   tn.name as clinic_name, t.subject, t.category, t.priority, t.status, t.page, t.trace_id,
                   t.error_message, t.user_agent, t.related_ticket_id, r.protocol as related_protocol,
                   t.first_response_due_at, t.first_response_at, t.waiting_since, t.resolved_at, t.closed_at,
                   t.rating, t.rating_comment, t.created_at, t.last_activity_at, t.clinic_unread, t.support_unread,
            """ + OVERDUE + """
             as overdue
            from support_tickets t
            join users u on u.id = t.opened_by
            join tenants tn on tn.id = t.tenant_id
            left join support_tickets r on r.id = t.related_ticket_id
            """;

    private final JdbcClient jdbc;

    public SupportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record TicketRow(
            UUID id,
            UUID tenantId,
            String protocol,
            UUID openedBy,
            String openedByName,
            String openedByEmail,
            String clinicName,
            String subject,
            String category,
            String priority,
            String status,
            String page,
            String traceId,
            String errorMessage,
            String userAgent,
            UUID relatedTicketId,
            String relatedProtocol,
            Instant firstResponseDueAt,
            Instant firstResponseAt,
            Instant waitingSince,
            Instant resolvedAt,
            Instant closedAt,
            Integer rating,
            String ratingComment,
            Instant createdAt,
            Instant lastActivityAt,
            int clinicUnread,
            int supportUnread,
            boolean overdue) {}

    public record JobTicket(UUID id, UUID tenantId, String protocol, String subject, String openedByEmail) {}

    public record StoredAttachment(UUID id, UUID tenantId, String filename, String contentType, String storageKey) {}

    public record PendingNotification(UUID id, String recipient, String subject, String body, int attempts) {}

    public record TicketChange(
            String status,
            Instant waitingSince,
            Instant remindedAt,
            Instant resolvedAt,
            boolean autoResolved,
            Instant closedAt,
            int reopenedCount) {}

    public long nextProtocolNumber() {
        return jdbc.sql("select nextval('support_protocol_seq')")
                .query(Long.class)
                .single();
    }

    public int ticketsOpenedSince(UUID tenantId, Instant since) {
        return jdbc.sql("select count(*) from support_tickets where tenant_id = ? and created_at >= ?")
                .params(tenantId, ts(since))
                .query(Integer.class)
                .single();
    }

    public boolean exists(UUID tenantId, UUID id) {
        return jdbc.sql("select count(*) from support_tickets where tenant_id = ? and id = ?")
                        .params(tenantId, id)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    public void insertTicket(
            UUID id,
            UUID tenantId,
            String protocol,
            UUID openedBy,
            String subject,
            String category,
            String priority,
            String page,
            String traceId,
            String errorMessage,
            String userAgent,
            UUID relatedTicketId,
            Instant firstResponseDueAt) {
        jdbc.sql("""
                        insert into support_tickets (id, tenant_id, protocol, opened_by, subject, category, priority,
                            page, trace_id, error_message, user_agent, related_ticket_id, first_response_due_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        protocol,
                        openedBy,
                        subject,
                        category,
                        priority,
                        page,
                        traceId,
                        errorMessage,
                        userAgent,
                        relatedTicketId,
                        ts(firstResponseDueAt))
                .update();
    }

    public Optional<TicketRow> find(UUID id) {
        return jdbc.sql(TICKET + " where t.id = ?")
                .param(id)
                .query(TicketRow.class)
                .optional();
    }

    public int reopenedCount(UUID id) {
        return jdbc.sql("select reopened_count from support_tickets where id = ?")
                .param(id)
                .query(Integer.class)
                .single();
    }

    public List<TicketSummary> clinicTickets(UUID tenantId, UUID userId, boolean allOfClinic, String status) {
        return jdbc.sql("""
                        select t.id, t.protocol, t.subject, t.category, t.priority, t.status,
                               u.name as opened_by_name, t.created_at, t.last_activity_at, t.first_response_due_at,
                               t.first_response_at,
                        """ + OVERDUE + """
                         as overdue, t.clinic_unread as unread_count
                        from support_tickets t join users u on u.id = t.opened_by
                        where t.tenant_id = ? and (? or t.opened_by = ?)
                          and (cast(? as text) is null or t.status = ?)
                        order by (t.clinic_unread = 0), t.last_activity_at desc
                        """)
                .params(tenantId, allOfClinic, userId, status, status)
                .query(TicketSummary.class)
                .list();
    }

    public List<PlatformTicketSummary> platformTickets(
            String status, String priority, UUID tenantId, String like, boolean onlyUnread) {
        return jdbc.sql("""
                        select t.id, t.protocol, t.tenant_id, tn.name as clinic_name, t.subject, t.category,
                               t.priority, t.status, u.name as opened_by_name, t.created_at, t.last_activity_at,
                               t.first_response_due_at, t.first_response_at,
                        """ + OVERDUE + """
                         as overdue, t.support_unread as unread_count
                        from support_tickets t
                        join users u on u.id = t.opened_by
                        join tenants tn on tn.id = t.tenant_id
                        where (cast(? as text) is null or t.status = ?)
                          and (cast(? as text) is null or t.priority = ?)
                          and (cast(? as uuid) is null or t.tenant_id = ?)
                          and (cast(? as text) is null or lower(t.protocol) like ? or lower(t.subject) like ?
                               or lower(tn.name) like ?)
                          and (not ? or t.support_unread > 0)
                        order by (t.support_unread = 0),
                                 case when t.support_unread > 0 then t.last_activity_at end desc nulls last,
                                 (t.status in ('RESOLVED', 'CLOSED')),
                                 case t.priority when 'URGENT' then 0 when 'HIGH' then 1 when 'NORMAL' then 2 else 3 end,
                                 t.last_activity_at desc
                        limit 500
                        """)
                .params(status, status, priority, priority, tenantId, tenantId, like, like, like, like, onlyUnread)
                .query(PlatformTicketSummary.class)
                .list();
    }

    public int unreadForClinic(UUID tenantId, UUID userId, boolean allOfClinic) {
        return jdbc.sql("""
                        select coalesce(sum(clinic_unread), 0) from support_tickets
                        where tenant_id = ? and (? or opened_by = ?)
                        """)
                .params(tenantId, allOfClinic, userId)
                .query(Integer.class)
                .single();
    }

    public int unreadForSupport() {
        return jdbc.sql("select coalesce(sum(support_unread), 0) from support_tickets where status <> 'CLOSED'")
                .query(Integer.class)
                .single();
    }

    public void insertMessage(
            UUID id,
            UUID tenantId,
            UUID ticketId,
            String authorType,
            UUID authorUserId,
            String body,
            boolean internal) {
        jdbc.sql("""
                        insert into support_messages (id, tenant_id, ticket_id, author_type, author_user_id, body, internal)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, ticketId, authorType, authorUserId, body, internal)
                .update();
    }

    public List<TicketMessageView> messages(UUID ticketId, boolean includeInternal) {
        return jdbc.sql("""
                        select m.id, m.author_type, u.name as author_name, m.body, m.internal, m.created_at
                        from support_messages m left join users u on u.id = m.author_user_id
                        where m.ticket_id = ? and (? or not m.internal)
                        order by m.created_at, m.id
                        """)
                .params(ticketId, includeInternal)
                .query(TicketMessageView.class)
                .list();
    }

    public void insertAttachment(
            UUID id,
            UUID tenantId,
            UUID ticketId,
            String authorType,
            UUID uploadedBy,
            String filename,
            String contentType,
            long sizeBytes,
            String storageKey) {
        jdbc.sql("""
                        insert into support_attachments (id, tenant_id, ticket_id, author_type, uploaded_by, filename,
                            content_type, size_bytes, storage_key)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, ticketId, authorType, uploadedBy, filename, contentType, sizeBytes, storageKey)
                .update();
    }

    public int attachmentCount(UUID ticketId) {
        return jdbc.sql("select count(*) from support_attachments where ticket_id = ?")
                .param(ticketId)
                .query(Integer.class)
                .single();
    }

    public List<TicketAttachmentView> attachments(UUID ticketId) {
        return jdbc.sql("""
                        select id, author_type, filename, content_type, size_bytes, created_at,
                               (purged_at is not null) as purged
                        from support_attachments where ticket_id = ? order by created_at, id
                        """).param(ticketId).query(TicketAttachmentView.class).list();
    }

    public Optional<StoredAttachment> attachment(UUID ticketId, UUID attachmentId) {
        return jdbc.sql("""
                        select id, tenant_id, filename, content_type, storage_key
                        from support_attachments where ticket_id = ? and id = ? and purged_at is null
                        """)
                .params(ticketId, attachmentId)
                .query(StoredAttachment.class)
                .optional();
    }

    public void changeTicket(UUID id, TicketChange c) {
        jdbc.sql("""
                        update support_tickets
                        set status = ?, waiting_since = ?, reminded_at = ?, resolved_at = ?, auto_resolved = ?,
                            closed_at = ?, reopened_count = ?, last_activity_at = now()
                        where id = ?
                        """)
                .params(
                        c.status(),
                        ts(c.waitingSince()),
                        ts(c.remindedAt()),
                        ts(c.resolvedAt()),
                        c.autoResolved(),
                        ts(c.closedAt()),
                        c.reopenedCount(),
                        id)
                .update();
    }

    public Optional<Instant> remindedAt(UUID id) {
        return jdbc.sql("select reminded_at from support_tickets where id = ?")
                .param(id)
                .query(Instant.class)
                .optional();
    }

    public void markActivityFromClinic(UUID id) {
        jdbc.sql(
                        "update support_tickets set support_unread = support_unread + 1, last_activity_at = now() where id = ?")
                .param(id)
                .update();
    }

    public void markAttachmentFromClinic(UUID id) {
        jdbc.sql("""
                        update support_tickets
                        set support_unread = greatest(support_unread, 1), last_activity_at = now()
                        where id = ?
                        """).param(id).update();
    }

    public void markAttachmentFromSupport(UUID id) {
        jdbc.sql("""
                        update support_tickets
                        set clinic_unread = greatest(clinic_unread, 1), last_activity_at = now()
                        where id = ?
                        """).param(id).update();
    }

    public void markPublicReplyFromSupport(UUID id) {
        jdbc.sql("""
                        update support_tickets
                        set first_response_at = coalesce(first_response_at, now()), clinic_unread = clinic_unread + 1,
                            support_unread = 0, last_activity_at = now()
                        where id = ?
                        """).param(id).update();
    }

    public void markClinicRead(UUID id) {
        jdbc.sql("update support_tickets set clinic_unread = 0 where id = ? and clinic_unread > 0")
                .param(id)
                .update();
    }

    public void markSupportRead(UUID id) {
        jdbc.sql("update support_tickets set support_unread = 0 where id = ? and support_unread > 0")
                .param(id)
                .update();
    }

    public void markClinicUnread(UUID id) {
        jdbc.sql("update support_tickets set clinic_unread = clinic_unread + 1 where id = ?")
                .param(id)
                .update();
    }

    public void setPriority(UUID id, String priority, Instant firstResponseDueAt) {
        jdbc.sql("update support_tickets set priority = ?, first_response_due_at = ? where id = ?")
                .params(priority, ts(firstResponseDueAt), id)
                .update();
    }

    public void rate(UUID id, int rating, String comment) {
        jdbc.sql("update support_tickets set rating = ?, rating_comment = ?, rated_at = now() where id = ?")
                .params(rating, comment, id)
                .update();
    }

    public void markReminded(UUID id, Instant at) {
        jdbc.sql("update support_tickets set reminded_at = ? where id = ?")
                .params(ts(at), id)
                .update();
    }

    public void markBreachNotified(UUID id) {
        jdbc.sql("update support_tickets set sla_breach_notified_at = now() where id = ?")
                .param(id)
                .update();
    }

    public List<String> platformAdminEmails() {
        return jdbc.sql("""
                        select email from users where platform_role = 'PLATFORM_ADMIN' and status = 'ACTIVE'
                        order by email
                        """).query(String.class).list();
    }

    public List<JobTicket> waitingToRemind(Instant before) {
        return jobTickets("t.status = 'WAITING_CLINIC' and t.reminded_at is null and t.waiting_since < ?", before);
    }

    public List<JobTicket> waitingToResolve(Instant before) {
        return jobTickets("t.status = 'WAITING_CLINIC' and t.reminded_at < ?", before);
    }

    public List<JobTicket> resolvedToClose(Instant before) {
        return jobTickets("t.status = 'RESOLVED' and t.resolved_at < ?", before);
    }

    public List<JobTicket> breachedWithoutNotice(Instant now) {
        return jobTickets(
                "t.first_response_at is null and t.status in ('OPEN', 'IN_PROGRESS')"
                        + " and t.sla_breach_notified_at is null and t.first_response_due_at < ?",
                now);
    }

    private List<JobTicket> jobTickets(String condition, Instant moment) {
        return jdbc.sql("select t.id, t.tenant_id, t.protocol, t.subject, u.email as opened_by_email"
                        + " from support_tickets t join users u on u.id = t.opened_by where " + condition
                        + " order by t.created_at limit 200")
                .param(ts(moment))
                .query(JobTicket.class)
                .list();
    }

    public List<StoredAttachment> attachmentsToPurge(Instant closedBefore) {
        return jdbc.sql("""
                        select a.id, a.tenant_id, a.filename, a.content_type, a.storage_key
                        from support_attachments a join support_tickets t on t.id = a.ticket_id
                        where a.purged_at is null and t.status = 'CLOSED' and t.closed_at < ?
                        limit 500
                        """)
                .param(ts(closedBefore))
                .query(StoredAttachment.class)
                .list();
    }

    public void markPurged(UUID attachmentId) {
        jdbc.sql("update support_attachments set purged_at = now() where id = ?")
                .param(attachmentId)
                .update();
    }

    public record MetricsRow(
            int open,
            int urgentOpen,
            int overdue,
            int waitingClinic,
            int unread,
            int openedLast30Days,
            Integer avgFirstResponseMinutes,
            BigDecimal avgRating,
            int ratedCount) {}

    public MetricsRow metrics() {
        return jdbc.sql("""
                        select
                          count(*) filter (where t.status in ('OPEN', 'IN_PROGRESS', 'WAITING_CLINIC')) as open,
                          count(*) filter (where t.priority = 'URGENT' and t.status in ('OPEN', 'IN_PROGRESS'))
                              as urgent_open,
                          count(*) filter (where
                        """ + OVERDUE + """
                        ) as overdue,
                          count(*) filter (where t.status = 'WAITING_CLINIC') as waiting_clinic,
                          coalesce(sum(t.support_unread) filter (where t.status <> 'CLOSED'), 0) as unread,
                          count(*) filter (where t.created_at >= now() - interval '30 days') as opened_last30_days,
                          round(avg(extract(epoch from (t.first_response_at - t.created_at)) / 60)
                              filter (where t.first_response_at is not null
                                  and t.created_at >= now() - interval '30 days'))::int as avg_first_response_minutes,
                          round(avg(t.rating), 1) as avg_rating,
                          count(t.rating) as rated_count
                        from support_tickets t
                        """).query(MetricsRow.class).single();
    }

    public List<CannedResponseView> cannedResponses(boolean onlyActive) {
        return jdbc.sql("""
                        select id, title, body, active, updated_at from support_canned_responses
                        where (not ? or active) order by title
                        """).param(onlyActive).query(CannedResponseView.class).list();
    }

    public Optional<CannedResponseView> cannedResponse(UUID id) {
        return jdbc.sql("select id, title, body, active, updated_at from support_canned_responses where id = ?")
                .param(id)
                .query(CannedResponseView.class)
                .optional();
    }

    public void insertCannedResponse(UUID id, String title, String body, boolean active) {
        jdbc.sql("insert into support_canned_responses (id, title, body, active) values (?, ?, ?, ?)")
                .params(id, title, body, active)
                .update();
    }

    public int updateCannedResponse(UUID id, String title, String body, Boolean active) {
        return jdbc.sql("""
                        update support_canned_responses
                        set title = ?, body = ?, active = coalesce(?, active), updated_at = now()
                        where id = ?
                        """).params(title, body, active, id).update();
    }

    public void insertNotification(UUID id, UUID ticketId, String recipient, String subject, String body) {
        jdbc.sql("""
                        insert into support_notifications (id, ticket_id, recipient, subject, body)
                        values (?, ?, ?, ?, ?)
                        """).params(id, ticketId, recipient, subject, body).update();
    }

    public List<PendingNotification> pendingNotifications(int limit) {
        return jdbc.sql("""
                        select id, recipient, subject, body, attempts from support_notifications
                        where status = 'PENDING' order by created_at limit ?
                        """).param(limit).query(PendingNotification.class).list();
    }

    public void markNotificationSent(UUID id) {
        jdbc.sql("""
                        update support_notifications set status = 'SENT', sent_at = now(), attempts = attempts + 1
                        where id = ?
                        """).param(id).update();
    }

    public void markNotificationFailed(UUID id, String error, boolean giveUp) {
        jdbc.sql("""
                        update support_notifications
                        set attempts = attempts + 1, last_error = ?, status = case when ? then 'FAILED' else status end
                        where id = ?
                        """).params(error, giveUp, id).update();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
