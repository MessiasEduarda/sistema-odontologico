package br.com.odontosaas.scheduling.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.scheduling.dto.request.AppointmentRequest;
import br.com.odontosaas.scheduling.dto.response.AppointmentDetail;
import br.com.odontosaas.scheduling.dto.response.AppointmentEventView;
import br.com.odontosaas.scheduling.dto.response.AppointmentView;
import br.com.odontosaas.scheduling.dto.response.FreeSlot;
import br.com.odontosaas.scheduling.event.AppointmentChanged;
import br.com.odontosaas.scheduling.repository.AppointmentRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AppointmentService {

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

    private final AppointmentRepository repository;
    private final TenantRefs refs;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final ScheduleBlockService blocks;

    public AppointmentService(
            AppointmentRepository repository,
            TenantRefs refs,
            AuditService audit,
            ApplicationEventPublisher events,
            JsonMapper json,
            ScheduleBlockService blocks) {
        this.repository = repository;
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
        return repository.list(tenantId, from, to, professionalId, patientId, status);
    }

    @Transactional(readOnly = true)
    public AppointmentView get(UUID id) {
        return repository
                .find(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> ApiException.notFound("Agendamento"));
    }

    @Transactional(readOnly = true)
    public AppointmentDetail detail(UUID id) {
        AppointmentView appointment = get(id);
        List<AppointmentEventView> history = repository.history(TenantContext.requireTenantId(), id).stream()
                .map(e -> new AppointmentEventView(e.action(), parse(e.details()), e.actorName(), e.occurredAt()))
                .toList();
        return new AppointmentDetail(appointment, history);
    }

    @Transactional
    public AppointmentView create(AppointmentRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        validate(tenantId, r);
        UUID id = Ids.newId();
        repository.insert(
                id, tenantId, r, blank(r.notes()), TenantContext.userId().orElse(null));
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
        int updated = repository.update(tenantId, id, r, blank(r.notes()), rescheduled);
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
        int updated = repository.updateStatus(tenantId, current.id(), current.version(), status, blank(reason));
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
        ZoneId zone = ZoneId.of(repository.clinicTimeZone(tenantId));
        List<AppointmentRepository.ScheduleWindowRow> windows = repository.scheduleWindows(
                tenantId, professionalId, date.getDayOfWeek().getValue());
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<AppointmentRepository.BusyRow> busy =
                new ArrayList<>(repository.busyPeriods(tenantId, professionalId, dayStart, dayEnd));
        for (Instant[] period : blocks.blockedPeriods(tenantId, professionalId, dayStart, dayEnd)) {
            busy.add(new AppointmentRepository.BusyRow(period[0], period[1]));
        }

        Duration step = Duration.ofMinutes(durationMinutes);
        Instant now = Instant.now();
        List<FreeSlot> free = new ArrayList<>();
        for (AppointmentRepository.ScheduleWindowRow w : windows) {
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
        repository.insertEvent(Ids.newId(), tenantId, appointmentId, action, json.writeValueAsString(details), actor);
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
