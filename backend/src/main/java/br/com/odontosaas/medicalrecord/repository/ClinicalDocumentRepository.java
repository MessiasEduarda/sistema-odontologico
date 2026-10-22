package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.response.DocumentView;
import br.com.odontosaas.medicalrecord.dto.response.IssuedDocument;
import java.util.List;
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
                               d.type, d.title, d.content, d.issued_at
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
            UUID createdBy) {
        jdbc.sql("""
                        insert into clinical_documents (id, tenant_id, patient_id, professional_id, type, title, content,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, patientId, professionalId, type, title, content, createdBy)
                .update();
    }

    public List<IssuedDocument> findRecent(UUID tenantId, String type) {
        return jdbc.sql("""
                        select d.id, d.patient_id, pa.full_name as patient_name, p.name as professional_name, d.type,
                               d.title, d.issued_at
                        from clinical_documents d
                        join patients pa on pa.tenant_id = d.tenant_id and pa.id = d.patient_id
                        join professionals p on p.tenant_id = d.tenant_id and p.id = d.professional_id
                        where d.tenant_id = :tenant and (cast(:type as varchar) is null or d.type = :type)
                        order by d.issued_at desc limit 200
                        """)
                .param("tenant", tenantId)
                .param("type", type)
                .query(IssuedDocument.class)
                .list();
    }
}
