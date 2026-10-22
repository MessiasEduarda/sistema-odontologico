package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.response.NoteView;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClinicalNoteRepository {

    private final JdbcClient jdbc;

    public ClinicalNoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<NoteView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select n.id, n.content, n.professional_id, p.name as professional_name, n.appointment_id,
                               n.amends_note_id, u.name as created_by_name, n.created_at
                        from clinical_notes n
                        left join professionals p on p.tenant_id = n.tenant_id and p.id = n.professional_id
                        left join users u on u.id = n.created_by
                        where n.tenant_id = ? and n.patient_id = ? order by n.created_at desc
                        """).params(tenantId, patientId).query(NoteView.class).list();
    }

    public boolean belongsToPatient(UUID tenantId, UUID noteId, UUID patientId) {
        return jdbc.sql("select exists(select 1 from clinical_notes where tenant_id = ? and id = ? and patient_id = ?)")
                .params(tenantId, noteId, patientId)
                .query(Boolean.class)
                .single();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID professionalId,
            UUID appointmentId,
            String content,
            UUID amendsNoteId,
            UUID createdBy) {
        jdbc.sql("""
                        insert into clinical_notes (id, tenant_id, patient_id, professional_id, appointment_id, content,
                            amends_note_id, created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, patientId, professionalId, appointmentId, content, amendsNoteId, createdBy)
                .update();
    }
}
