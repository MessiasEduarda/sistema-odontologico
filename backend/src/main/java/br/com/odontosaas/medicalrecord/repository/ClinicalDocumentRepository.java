package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.response.DocumentView;
import br.com.odontosaas.medicalrecord.dto.response.IssuedDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClinicalDocumentRepository {

    private final JdbcClient jdbc;

    public ClinicalDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<DocumentView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select d.id, d.professional_id, p.name as professional_name,
                               case when p.cro is not null then 'CRO-' || coalesce(p.cro_state, '') || ' ' || p.cro end
                                   as professional_cro,
                               d.type, d.title, d.content, d.issued_at, d.shared_with_patient, d.updated_at,
                               d.voided_at, d.void_reason
                        from clinical_documents d
                        join professionals p on p.tenant_id = d.tenant_id and p.id = d.professional_id
                        where d.tenant_id = ? and d.patient_id = ? order by d.issued_at desc
                        """)
                .params(tenantId, patientId)
                .query(DocumentView.class)
                .list();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID professionalId,
            String type,
            String title,
            String content,
            boolean sharedWithPatient,
            UUID createdBy) {
        jdbc.sql("""
                        insert into clinical_documents (id, tenant_id, patient_id, professional_id, type, title, content,
                            shared_with_patient, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, patientId, professionalId, type, title, content, sharedWithPatient, createdBy)
                .update();
    }

    public record DocumentHeadRow(UUID professionalId, String title, String content, boolean voided) {}

    public Optional<DocumentHeadRow> findHead(UUID tenantId, UUID patientId, UUID documentId) {
        return jdbc.sql("""
                        select professional_id, title, content, voided_at is not null as voided
                        from clinical_documents where tenant_id = ? and patient_id = ? and id = ?
                        """)
                .params(tenantId, patientId, documentId)
                .query(DocumentHeadRow.class)
                .optional();
    }

    public void insertRevision(UUID id, UUID tenantId, UUID documentId, String title, String content, UUID revisedBy) {
        jdbc.sql("""
                        insert into clinical_document_revisions (id, tenant_id, document_id, title, content, revised_by)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, documentId, title, content, revisedBy)
                .update();
    }

    public void updateContent(UUID tenantId, UUID documentId, String title, String content, UUID updatedBy) {
        jdbc.sql("""
                        update clinical_documents set title = ?, content = ?, updated_at = now(), updated_by = ?
                        where tenant_id = ? and id = ? and voided_at is null
                        """).params(title, content, updatedBy, tenantId, documentId).update();
    }

    public int voidDocument(UUID tenantId, UUID patientId, UUID documentId, String reason, UUID voidedBy) {
        return jdbc.sql("""
                        update clinical_documents set voided_at = now(), voided_by = ?, void_reason = ?,
                            shared_with_patient = false
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        """)
                .params(voidedBy, reason, tenantId, patientId, documentId)
                .update();
    }

    public int updateSharing(UUID tenantId, UUID patientId, UUID documentId, boolean shared) {
        return jdbc.sql("""
                        update clinical_documents set shared_with_patient = ?
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        """).params(shared, tenantId, patientId, documentId).update();
    }

    public List<IssuedDocument> findRecent(UUID tenantId, String type) {
        return jdbc.sql("""
                        select d.id, d.patient_id, pa.full_name as patient_name, p.name as professional_name, d.type,
                               d.title, d.issued_at
                        from clinical_documents d
                        join patients pa on pa.tenant_id = d.tenant_id and pa.id = d.patient_id
                        join professionals p on p.tenant_id = d.tenant_id and p.id = d.professional_id
                        where d.tenant_id = :tenant and d.voided_at is null
                          and (cast(:type as varchar) is null or d.type = :type)
                        order by d.issued_at desc limit 200
                        """)
                .param("tenant", tenantId)
                .param("type", type)
                .query(IssuedDocument.class)
                .list();
    }
}
