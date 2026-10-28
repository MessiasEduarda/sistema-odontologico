package br.com.odontosaas.helpdesk.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.helpdesk.dto.request.CannedResponseRequest;
import br.com.odontosaas.helpdesk.dto.request.NewTicketRequest;
import br.com.odontosaas.helpdesk.dto.request.SupportReplyRequest;
import br.com.odontosaas.helpdesk.dto.response.CannedResponseView;
import br.com.odontosaas.helpdesk.dto.response.PlatformTicketSummary;
import br.com.odontosaas.helpdesk.dto.response.SupportContact;
import br.com.odontosaas.helpdesk.dto.response.SupportMetrics;
import br.com.odontosaas.helpdesk.dto.response.TicketDetail;
import br.com.odontosaas.helpdesk.dto.response.TicketSummary;
import br.com.odontosaas.helpdesk.dto.response.UnreadCount;
import br.com.odontosaas.helpdesk.repository.SupportRepository;
import br.com.odontosaas.helpdesk.repository.SupportRepository.JobTicket;
import br.com.odontosaas.helpdesk.repository.SupportRepository.MetricsRow;
import br.com.odontosaas.helpdesk.repository.SupportRepository.StoredAttachment;
import br.com.odontosaas.helpdesk.repository.SupportRepository.TicketChange;
import br.com.odontosaas.helpdesk.repository.SupportRepository.TicketRow;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.tenancy.TenantContext;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SupportService {

    public static final int MAX_TICKETS_PER_DAY = 10;
    public static final int MAX_ATTACHMENTS_PER_TICKET = 6;
    public static final Duration REMIND_AFTER = Duration.ofDays(5);
    public static final Duration AUTO_RESOLVE_AFTER_REMINDER = Duration.ofDays(5);
    public static final Duration CLOSE_AFTER_RESOLVED = Duration.ofDays(7);
    public static final Duration PURGE_ATTACHMENTS_AFTER = Duration.ofDays(90);

    private final SupportRepository repository;
    private final SupportNotifier notifier;
    private final SupportFiles files;
    private final AuditService audit;
    private final ZoneId zone;
    private final String contactEmail;

    public SupportService(
            SupportRepository repository,
            SupportNotifier notifier,
            SupportFiles files,
            AuditService audit,
            @Value("${app.support.time-zone:America/Sao_Paulo}") String timeZone,
            @Value("${app.support.contact-email:}") String contactEmail) {
        this.repository = repository;
        this.notifier = notifier;
        this.files = files;
        this.audit = audit;
        this.zone = ZoneId.of(timeZone);
        this.contactEmail = contactEmail == null || contactEmail.isBlank() ? null : contactEmail.strip();
    }

    public record Viewer(UUID userId, boolean manager) {}

    public SupportContact contact() {
        return new SupportContact(contactEmail, SupportHours.LABEL);
    }

    @Transactional
    public TicketDetail open(NewTicketRequest r, Viewer viewer) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = Instant.now();
        if (repository.ticketsOpenedSince(tenantId, now.minus(Duration.ofDays(1))) >= MAX_TICKETS_PER_DAY) {
            throw ApiException.businessRule(
                    "SUPPORT_TICKET_LIMIT",
                    "A clínica abriu muitos chamados nas últimas 24 horas. Responda em um chamado já aberto.");
        }
        if (r.relatedTicketId() != null && !repository.exists(tenantId, r.relatedTicketId())) {
            throw ApiException.notFound("Chamado relacionado");
        }
        String priority = SupportHours.priorityFromImpact(r.impact());
        UUID id = Ids.newId();
        String protocol = "SUP-%d-%05d".formatted(now.atZone(zone).getYear(), repository.nextProtocolNumber());
        repository.insertTicket(
                id,
                tenantId,
                protocol,
                viewer.userId(),
                r.subject().strip(),
                r.category(),
                priority,
                blank(r.page()),
                blank(r.traceId()),
                blank(r.errorMessage()),
                RequestInfo.userAgent(),
                r.relatedTicketId(),
                SupportHours.addBusinessMinutes(now, SupportHours.slaMinutes(priority), zone));
        repository.insertMessage(
                Ids.newId(),
                tenantId,
                id,
                "CLINIC",
                viewer.userId(),
                r.description().strip(),
                false);
        audit.record(AuditAction.SUPPORT_TICKET_OPENED, "support_ticket", id, Map.of("priority", priority));
        notifier.ticketOpened(require(id));
        return detail(require(id), false);
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> clinicTickets(Viewer viewer, String status) {
        return repository.clinicTickets(
                TenantContext.requireTenantId(), viewer.userId(), viewer.manager(), blank(status));
    }

    @Transactional
    public TicketDetail clinicDetail(UUID id, Viewer viewer) {
        TicketRow row = clinicTicket(id, viewer);
        repository.markClinicRead(id);
        return detail(require(row.id()), false);
    }

    @Transactional
    public TicketDetail clinicReply(UUID id, String body, Viewer viewer) {
        TicketRow row = clinicTicket(id, viewer);
        requireOpen(row);
        repository.insertMessage(Ids.newId(), row.tenantId(), id, "CLINIC", viewer.userId(), body.strip(), false);
        if (row.status().equals("WAITING_CLINIC") || row.status().equals("RESOLVED")) {
            change(row, "IN_PROGRESS", false);
        }
        repository.markActivityFromClinic(id);
        notifier.clinicReplied(row);
        return detail(require(id), false);
    }

    @Transactional
    public TicketDetail clinicResolve(UUID id, Viewer viewer) {
        TicketRow row = clinicTicket(id, viewer);
        requireOpen(row);
        if (!row.status().equals("RESOLVED")) {
            change(row, "RESOLVED", false);
            repository.insertMessage(
                    Ids.newId(),
                    row.tenantId(),
                    id,
                    "SYSTEM",
                    viewer.userId(),
                    "A clínica marcou o chamado como resolvido.",
                    false);
            repository.markActivityFromClinic(id);
        }
        return detail(require(id), false);
    }

    @Transactional
    public TicketDetail rate(UUID id, int rating, String comment, Viewer viewer) {
        TicketRow row = clinicTicket(id, viewer);
        if (!row.status().equals("RESOLVED") && !row.status().equals("CLOSED")) {
            throw ApiException.businessRule(
                    "TICKET_NOT_RESOLVED", "Avalie o atendimento depois que o chamado for resolvido.");
        }
        repository.rate(id, rating, blank(comment));
        return detail(require(id), false);
    }

    @Transactional
    public TicketDetail clinicAttach(UUID id, MultipartFile file, Viewer viewer) {
        TicketRow row = clinicTicket(id, viewer);
        attach(row, file, "CLINIC", viewer.userId());
        repository.markAttachmentFromClinic(id);
        return detail(require(id), false);
    }

    @Transactional(readOnly = true)
    public AttachmentFile clinicDownload(UUID id, UUID attachmentId, Viewer viewer) {
        clinicTicket(id, viewer);
        return file(id, attachmentId);
    }

    @Transactional(readOnly = true)
    public UnreadCount unread(Viewer viewer) {
        return new UnreadCount(
                repository.unreadForClinic(TenantContext.requireTenantId(), viewer.userId(), viewer.manager()));
    }

    @Transactional(readOnly = true)
    public UnreadCount platformUnread() {
        return new UnreadCount(repository.unreadForSupport());
    }

    @Transactional(readOnly = true)
    public List<PlatformTicketSummary> platformTickets(
            String status, String priority, UUID tenantId, String query, boolean onlyUnread) {
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return repository.platformTickets(blank(status), blank(priority), tenantId, like, onlyUnread);
    }

    @Transactional
    public TicketDetail platformDetail(UUID id) {
        TicketRow row = require(id);
        repository.markSupportRead(id);
        return detail(row, true);
    }

    @Transactional
    public TicketDetail platformReply(UUID id, SupportReplyRequest r, UUID adminId) {
        TicketRow row = require(id);
        requireOpen(row);
        boolean internal = Boolean.TRUE.equals(r.internal());
        repository.insertMessage(
                Ids.newId(), row.tenantId(), id, "SUPPORT", adminId, r.body().strip(), internal);
        if (!internal) {
            repository.markPublicReplyFromSupport(id);
            notifier.supportReplied(row);
        }
        String target = r.status() != null ? r.status() : internal ? null : "WAITING_CLINIC";
        if (target == null && row.status().equals("OPEN")) target = "IN_PROGRESS";
        if (target != null && !target.equals(row.status())) applyStatus(require(id), target, adminId);
        audit.recordGlobal(
                AuditAction.SUPPORT_TICKET_UPDATED,
                adminId,
                "support_ticket",
                id,
                Map.of("reply", internal ? "internal" : "public"));
        return detail(require(id), true);
    }

    @Transactional
    public TicketDetail platformStatus(UUID id, String status, UUID adminId) {
        TicketRow row = require(id);
        if (row.status().equals("CLOSED")) {
            throw ApiException.conflict("TICKET_CLOSED", "Chamado encerrado não muda de situação.");
        }
        if (!status.equals(row.status())) applyStatus(row, status, adminId);
        audit.recordGlobal(AuditAction.SUPPORT_TICKET_UPDATED, adminId, "support_ticket", id, Map.of("status", status));
        return detail(require(id), true);
    }

    @Transactional
    public TicketDetail platformPriority(UUID id, String priority, UUID adminId) {
        TicketRow row = require(id);
        repository.setPriority(
                id,
                priority,
                SupportHours.addBusinessMinutes(row.createdAt(), SupportHours.slaMinutes(priority), zone));
        audit.recordGlobal(
                AuditAction.SUPPORT_TICKET_UPDATED, adminId, "support_ticket", id, Map.of("priority", priority));
        return detail(require(id), true);
    }

    @Transactional
    public TicketDetail platformAttach(UUID id, MultipartFile file, UUID adminId) {
        TicketRow row = require(id);
        attach(row, file, "SUPPORT", adminId);
        repository.markAttachmentFromSupport(id);
        return detail(require(id), true);
    }

    @Transactional
    public AttachmentFile platformDownload(UUID id, UUID attachmentId, UUID adminId) {
        require(id);
        AttachmentFile file = file(id, attachmentId);
        audit.recordGlobal(
                AuditAction.SUPPORT_ATTACHMENT_VIEWED,
                adminId,
                "support_attachment",
                attachmentId,
                Map.of("ticket", id));
        return file;
    }

    @Transactional(readOnly = true)
    public SupportMetrics metrics() {
        MetricsRow m = repository.metrics();
        return new SupportMetrics(
                m.open(),
                m.urgentOpen(),
                m.overdue(),
                m.waitingClinic(),
                m.unread(),
                m.openedLast30Days(),
                m.avgFirstResponseMinutes(),
                m.avgRating(),
                m.ratedCount());
    }

    @Transactional(readOnly = true)
    public List<CannedResponseView> cannedResponses(boolean onlyActive) {
        return repository.cannedResponses(onlyActive);
    }

    @Transactional
    public CannedResponseView createCannedResponse(CannedResponseRequest r) {
        UUID id = Ids.newId();
        repository.insertCannedResponse(id, r.title().strip(), r.body().strip(), r.active() == null || r.active());
        return repository.cannedResponse(id).orElseThrow();
    }

    @Transactional
    public CannedResponseView updateCannedResponse(UUID id, CannedResponseRequest r) {
        if (repository.updateCannedResponse(id, r.title().strip(), r.body().strip(), r.active()) == 0) {
            throw ApiException.notFound("Resposta pronta");
        }
        return repository.cannedResponse(id).orElseThrow();
    }

    @Transactional
    public Maintenance runMaintenance(Instant now) {
        int reminded = 0;
        for (JobTicket t : repository.waitingToRemind(now.minus(REMIND_AFTER))) {
            repository.markReminded(t.id(), now);
            repository.insertMessage(
                    Ids.newId(),
                    t.tenantId(),
                    t.id(),
                    "SYSTEM",
                    null,
                    "Lembrete enviado: o chamado aguarda resposta da clínica há 5 dias.",
                    false);
            repository.markClinicUnread(t.id());
            notifier.waitingReminder(t);
            reminded++;
        }
        int autoResolved = 0;
        for (JobTicket t : repository.waitingToResolve(now.minus(AUTO_RESOLVE_AFTER_REMINDER))) {
            change(require(t.id()), "RESOLVED", true, now);
            repository.insertMessage(
                    Ids.newId(),
                    t.tenantId(),
                    t.id(),
                    "SYSTEM",
                    null,
                    "Chamado marcado como resolvido por falta de resposta da clínica.",
                    false);
            repository.markClinicUnread(t.id());
            notifier.autoResolved(t);
            autoResolved++;
        }
        int closed = 0;
        for (JobTicket t : repository.resolvedToClose(now.minus(CLOSE_AFTER_RESOLVED))) {
            change(require(t.id()), "CLOSED", false, now);
            closed++;
        }
        int breached = 0;
        for (JobTicket t : repository.breachedWithoutNotice(now)) {
            repository.markBreachNotified(t.id());
            notifier.slaBreached(t);
            breached++;
        }
        int purged = 0;
        for (StoredAttachment a : repository.attachmentsToPurge(now.minus(PURGE_ATTACHMENTS_AFTER))) {
            files.delete(a.storageKey());
            repository.markPurged(a.id());
            purged++;
        }
        return new Maintenance(reminded, autoResolved, closed, breached, purged);
    }

    public record Maintenance(int reminded, int autoResolved, int closed, int breached, int purged) {}

    public record AttachmentFile(String filename, String contentType, Path path) {}

    private void applyStatus(TicketRow row, String status, UUID actorId) {
        change(row, status, false);
        String text = switch (status) {
            case "IN_PROGRESS" -> "Chamado em análise pela equipe de suporte.";
            case "WAITING_CLINIC" -> null;
            case "RESOLVED" -> "Chamado marcado como resolvido pela equipe de suporte.";
            default -> "Chamado encerrado.";
        };
        if (text != null) {
            repository.insertMessage(Ids.newId(), row.tenantId(), row.id(), "SYSTEM", actorId, text, false);
        }
        if (status.equals("RESOLVED")) {
            repository.markClinicUnread(row.id());
            notifier.resolved(row);
        }
    }

    private void change(TicketRow row, String status, boolean auto) {
        change(row, status, auto, Instant.now());
    }

    private void change(TicketRow row, String status, boolean auto, Instant now) {
        int reopened = repository.reopenedCount(row.id());
        TicketChange change = switch (status) {
            case "IN_PROGRESS" ->
                new TicketChange(
                        status,
                        null,
                        null,
                        null,
                        false,
                        null,
                        row.status().equals("RESOLVED") ? reopened + 1 : reopened);
            case "WAITING_CLINIC" -> new TicketChange(status, now, null, null, false, null, reopened);
            case "RESOLVED" -> new TicketChange(status, null, null, now, auto, null, reopened);
            default ->
                new TicketChange(
                        "CLOSED",
                        null,
                        repository.remindedAt(row.id()).orElse(null),
                        row.resolvedAt() != null ? row.resolvedAt() : now,
                        auto,
                        now,
                        reopened);
        };
        repository.changeTicket(row.id(), change);
    }

    private void attach(TicketRow row, MultipartFile file, String authorType, UUID userId) {
        requireOpen(row);
        if (repository.attachmentCount(row.id()) >= MAX_ATTACHMENTS_PER_TICKET) {
            throw ApiException.businessRule(
                    "SUPPORT_ATTACHMENT_LIMIT", "Cada chamado aceita até " + MAX_ATTACHMENTS_PER_TICKET + " anexos.");
        }
        UUID attachmentId = Ids.newId();
        SupportFiles.Upload upload = files.store(row.tenantId(), attachmentId, file);
        repository.insertAttachment(
                attachmentId,
                row.tenantId(),
                row.id(),
                authorType,
                userId,
                upload.filename(),
                upload.contentType(),
                upload.size(),
                upload.key());
    }

    private AttachmentFile file(UUID ticketId, UUID attachmentId) {
        StoredAttachment a =
                repository.attachment(ticketId, attachmentId).orElseThrow(() -> ApiException.notFound("Anexo"));
        return new AttachmentFile(a.filename(), a.contentType(), files.path(a.storageKey()));
    }

    private TicketRow clinicTicket(UUID id, Viewer viewer) {
        UUID tenantId = TenantContext.requireTenantId();
        return repository
                .find(id)
                .filter(t -> t.tenantId().equals(tenantId))
                .filter(t -> viewer.manager() || t.openedBy().equals(viewer.userId()))
                .orElseThrow(() -> ApiException.notFound("Chamado"));
    }

    private TicketRow require(UUID id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("Chamado"));
    }

    private static void requireOpen(TicketRow row) {
        if (row.status().equals("CLOSED")) {
            throw ApiException.conflict(
                    "TICKET_CLOSED",
                    "Este chamado foi encerrado. Abra um novo chamado e cite o protocolo " + row.protocol() + ".");
        }
    }

    private TicketDetail detail(TicketRow t, boolean forSupport) {
        boolean open = !t.status().equals("CLOSED");
        return new TicketDetail(
                t.id(),
                t.protocol(),
                t.tenantId(),
                t.clinicName(),
                t.subject(),
                t.category(),
                t.priority(),
                t.status(),
                t.openedByName(),
                t.openedByEmail(),
                t.createdAt(),
                t.firstResponseDueAt(),
                t.firstResponseAt(),
                t.overdue(),
                t.resolvedAt(),
                t.closedAt(),
                open,
                !forSupport && (t.status().equals("RESOLVED") || t.status().equals("CLOSED")),
                t.rating(),
                t.ratingComment(),
                t.page(),
                t.traceId(),
                t.errorMessage(),
                forSupport ? t.userAgent() : null,
                t.relatedTicketId(),
                t.relatedProtocol(),
                repository.messages(t.id(), forSupport),
                repository.attachments(t.id()));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
