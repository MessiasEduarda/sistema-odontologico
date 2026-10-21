package br.com.odontosaas.scheduling.repository;

import br.com.odontosaas.scheduling.dto.request.WaitlistRequest;
import br.com.odontosaas.scheduling.dto.response.WaitlistView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados da lista de espera (encaixe). */
@Repository
public class WaitlistRepository {

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

    public WaitlistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Aguardando: urgentes primeiro, depois por ordem de chegada. Demais status: as mais recentes primeiro. */
    public List<WaitlistView> list(UUID tenantId, String status) {
        String order =
                "WAITING".equals(status) ? " order by w.urgent desc, w.created_at" : " order by w.resolved_at desc";
        return jdbc.sql(SELECT + " where w.tenant_id = ? and w.status = ?" + order + " limit 500")
                .params(tenantId, status)
                .query(WaitlistView.class)
                .list();
    }

    public Optional<WaitlistView> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where w.tenant_id = ? and w.id = ?")
                .params(tenantId, id)
                .query(WaitlistView.class)
                .optional();
    }

    /** O paciente já tem entrada aguardando na lista? */
    public boolean isWaiting(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select exists(select 1 from waitlist_entries
                                      where tenant_id = ? and patient_id = ? and status = 'WAITING')
                        """).params(tenantId, patientId).query(Boolean.class).single();
    }

    public void insert(UUID id, UUID tenantId, WaitlistRequest r, String preference, String notes, UUID createdBy) {
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
                        preference,
                        notes,
                        r.urgent(),
                        createdBy)
                .update();
    }

    public void update(UUID tenantId, UUID id, WaitlistRequest r, String preference, String notes) {
        jdbc.sql("""
                        update waitlist_entries
                        set professional_id = ?, procedure_id = ?, preference = ?, notes = ?, urgent = ?
                        where tenant_id = ? and id = ?
                        """)
                .params(r.professionalId(), r.procedureId(), preference, notes, r.urgent(), tenantId, id)
                .update();
    }

    /** O atendimento existe na clínica e pertence ao paciente? */
    public boolean appointmentBelongsToPatient(UUID tenantId, UUID appointmentId, UUID patientId) {
        return jdbc.sql("""
                        select exists(select 1 from appointments
                                      where tenant_id = ? and id = ? and patient_id = ?)
                        """)
                .params(tenantId, appointmentId, patientId)
                .query(Boolean.class)
                .single();
    }

    /** Tira a entrada da lista (encaixada ou removida). */
    public void resolve(UUID tenantId, UUID id, String status, UUID appointmentId) {
        jdbc.sql("""
                        update waitlist_entries set status = ?, appointment_id = ?, resolved_at = now()
                        where tenant_id = ? and id = ?
                        """).params(status, appointmentId, tenantId, id).update();
    }
}
