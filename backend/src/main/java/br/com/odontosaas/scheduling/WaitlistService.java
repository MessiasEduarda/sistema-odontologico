package br.com.odontosaas.scheduling;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.scheduling.SchedulingDtos.WaitlistRequest;
import br.com.odontosaas.scheduling.SchedulingDtos.WaitlistResolveRequest;
import br.com.odontosaas.scheduling.SchedulingDtos.WaitlistView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista de espera (encaixe): quem aguarda vaga, com preferência de horário, para chamar quando abrir um horário. */
@Service
public class WaitlistService {

    private static final Set<String> STATUSES = Set.of("WAITING", "SCHEDULED", "REMOVED");

    private static final String SELECT = """
            select w.id, w.patient_id, p.full_name as patient_name, p.mobile as patient_mobile,
                   p.allow_whatsapp as patient_allow_whatsapp, w.professional_id, pr.name as professional_name,
                   w.procedure_id, pc.name as procedure_name, w.preference, w.notes, w.urgent, w.status,
                   w.appointment_id, w.created_at, w.resolved_at
            from waitlist_entries w
            join patients p on p.tenant_id = w.tenant_id and p.id = w.patient_id
            left join professionals pr on pr.tenant_id = w.tenant_id and pr.id = w.professional_id
            left join procedures pc on pc.tenant_id = w.tenant_id and pc.id = w.procedure_id
            """;

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;

    public WaitlistService(JdbcClient jdbc, TenantRefs refs, AuditService audit) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
    }

    /** Aguardando: urgentes primeiro, depois por ordem de chegada. Resolvidas: as mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<WaitlistView> list(String status) {
        String s = status == null || status.isBlank() ? "WAITING" : status;
        if (!STATUSES.contains(s)) throw ApiException.badRequest("INVALID_STATUS", "Status inválido.");
        String order = "WAITING".equals(s) ? " order by w.urgent desc, w.created_at" : " order by w.resolved_at desc";
        return jdbc.sql(SELECT + " where w.tenant_id = ? and w.status = ?" + order + " limit 500")
                .params(TenantContext.requireTenantId(), s)
                .query(WaitlistView.class)
                .list();
    }

    @Transactional
    public WaitlistView create(WaitlistRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, r.patientId(), "Paciente");
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        refs.require("procedures", tenantId, r.procedureId(), "Procedimento");
        boolean waiting = jdbc.sql("""
                        select exists(select 1 from waitlist_entries
                                      where tenant_id = ? and patient_id = ? and status = 'WAITING')
                        """)
                .params(tenantId, r.patientId())
                .query(Boolean.class)
                .single();
        if (waiting) {
            throw ApiException.conflict(
                    "ALREADY_WAITING", "Este paciente já está na lista de espera. Edite a entrada existente.");
        }
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into waitlist_entries
                            (id, tenant_id, patient_id, professional_id, procedure_id, preference, notes, urgent,
                             created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        r.patientId(),
                        r.professionalId(),
                        r.procedureId(),
                        blankToNull(r.preference()),
                        blankToNull(r.notes()),
                        r.urgent(),
                        TenantContext.userId().orElse(null))
                .update();
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
        jdbc.sql("""
                        update waitlist_entries
                        set professional_id = ?, procedure_id = ?, preference = ?, notes = ?, urgent = ?
                        where tenant_id = ? and id = ?
                        """)
                .params(
                        r.professionalId(),
                        r.procedureId(),
                        blankToNull(r.preference()),
                        blankToNull(r.notes()),
                        r.urgent(),
                        tenantId,
                        id)
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "waitlist-entry", id, Map.of());
        return get(tenantId, id);
    }

    /** Tira da lista: encaixado (com o atendimento já marcado para o mesmo paciente) ou desistência. */
    @Transactional
    public WaitlistView resolve(UUID id, WaitlistResolveRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        WaitlistView current = requireWaiting(tenantId, id);
        UUID appointmentId = null;
        if ("SCHEDULED".equals(r.status())) {
            if (r.appointmentId() == null) {
                throw ApiException.invalidField("appointmentId", "Informe o atendimento marcado.");
            }
            boolean samePatient = jdbc.sql("""
                            select exists(select 1 from appointments
                                          where tenant_id = ? and id = ? and patient_id = ?)
                            """)
                    .params(tenantId, r.appointmentId(), current.patientId())
                    .query(Boolean.class)
                    .single();
            if (!samePatient) throw ApiException.notFound("Atendimento");
            appointmentId = r.appointmentId();
        }
        jdbc.sql("""
                        update waitlist_entries set status = ?, appointment_id = ?, resolved_at = now()
                        where tenant_id = ? and id = ?
                        """).params(r.status(), appointmentId, tenantId, id).update();
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
        return jdbc.sql(SELECT + " where w.tenant_id = ? and w.id = ?")
                .params(tenantId, id)
                .query(WaitlistView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Entrada da lista de espera"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
