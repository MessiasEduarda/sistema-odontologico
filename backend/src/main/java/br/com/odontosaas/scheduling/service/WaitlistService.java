package br.com.odontosaas.scheduling.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.scheduling.dto.request.WaitlistRequest;
import br.com.odontosaas.scheduling.dto.request.WaitlistResolveRequest;
import br.com.odontosaas.scheduling.dto.response.WaitlistView;
import br.com.odontosaas.scheduling.repository.WaitlistRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitlistService {

    private static final Set<String> STATUSES = Set.of("WAITING", "SCHEDULED", "REMOVED");

    private final WaitlistRepository waitlist;
    private final TenantRefs refs;
    private final AuditService audit;

    public WaitlistService(WaitlistRepository waitlist, TenantRefs refs, AuditService audit) {
        this.waitlist = waitlist;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WaitlistView> list(String status) {
        String s = status == null || status.isBlank() ? "WAITING" : status;
        if (!STATUSES.contains(s)) throw ApiException.badRequest("INVALID_STATUS", "Status inválido.");
        return waitlist.list(TenantContext.requireTenantId(), s);
    }

    @Transactional
    public WaitlistView create(WaitlistRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, r.patientId(), "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        refs.require("procedures", tenantId, r.procedureId(), "Procedimento");
        boolean waiting = waitlist.isWaiting(tenantId, r.patientId());
        if (waiting) {
            throw ApiException.conflict(
                    "ALREADY_WAITING", "Este paciente já está na lista de espera. Edite a entrada existente.");
        }
        UUID id = Ids.newId();
        waitlist.insert(
                id,
                tenantId,
                r,
                blankToNull(r.preference()),
                blankToNull(r.notes()),
                TenantContext.userId().orElse(null));
        audit.record(AuditAction.RECORD_CREATED, "waitlist-entry", id, Map.of());
        return get(tenantId, id);
    }

    @Transactional
    public WaitlistView update(UUID id, WaitlistRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        WaitlistView current = requireWaiting(tenantId, id);
        if (!current.patientId().equals(r.patientId())) {
            throw ApiException.invalidField("patientId", "O paciente de uma entrada não pode ser trocado.");
        }
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        refs.require("procedures", tenantId, r.procedureId(), "Procedimento");
        waitlist.update(tenantId, id, r, blankToNull(r.preference()), blankToNull(r.notes()));
        audit.record(AuditAction.RECORD_UPDATED, "waitlist-entry", id, Map.of());
        return get(tenantId, id);
    }

    @Transactional
    public WaitlistView resolve(UUID id, WaitlistResolveRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        WaitlistView current = requireWaiting(tenantId, id);
        UUID appointmentId = null;
        if ("SCHEDULED".equals(r.status())) {
            if (r.appointmentId() == null) {
                throw ApiException.invalidField("appointmentId", "Informe o atendimento marcado.");
            }
            boolean samePatient =
                    waitlist.appointmentBelongsToPatient(tenantId, r.appointmentId(), current.patientId());
            if (!samePatient) throw ApiException.notFound("Atendimento");
            appointmentId = r.appointmentId();
        }
        waitlist.resolve(tenantId, id, r.status(), appointmentId);
        audit.record(AuditAction.RECORD_UPDATED, "waitlist-entry", id, Map.of("status", r.status()));
        return get(tenantId, id);
    }

    private WaitlistView requireWaiting(UUID tenantId, UUID id) {
        WaitlistView current = get(tenantId, id);
        if (!"WAITING".equals(current.status())) {
            throw ApiException.businessRule("WAITLIST_RESOLVED", "Esta entrada já saiu da lista de espera.");
        }
        return current;
    }

    private WaitlistView get(UUID tenantId, UUID id) {
        return waitlist.find(tenantId, id).orElseThrow(() -> ApiException.notFound("Entrada da lista de espera"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
