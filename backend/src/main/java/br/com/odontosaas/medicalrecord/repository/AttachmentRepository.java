package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.response.AttachmentView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados dos anexos do prontuário (tabela attachments). */
@Repository
public class AttachmentRepository {

    /** Metadados necessários para servir o arquivo de um anexo. */
    public record AttachmentFileRow(String filename, String contentType, String storageKey) {}

    private final JdbcClient jdbc;

    public AttachmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AttachmentView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select a.id, a.category, a.description, a.filename, a.content_type, a.size_bytes,
                               u.name as uploaded_by_name, a.created_at
                        from attachments a left join users u on u.id = a.uploaded_by
                        where a.tenant_id = ? and a.patient_id = ? and a.deleted_at is null
                        order by a.created_at desc
                        """)
                .params(tenantId, patientId)
                .query(AttachmentView.class)
                .list();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            String category,
            String description,
            String filename,
            String contentType,
            long sizeBytes,
            String sha256,
            String storageKey,
            UUID uploadedBy) {
        jdbc.sql("""
                        insert into attachments (id, tenant_id, patient_id, category, description, filename,
                            content_type, size_bytes, sha256, storage_key, uploaded_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        category,
                        description,
                        filename,
                        contentType,
                        sizeBytes,
                        sha256,
                        storageKey,
                        uploadedBy)
                .update();
    }

    public Optional<AttachmentFileRow> findFile(UUID tenantId, UUID patientId, UUID attachmentId) {
        return jdbc.sql("""
                        select filename, content_type, storage_key from attachments
                        where tenant_id = ? and patient_id = ? and id = ? and deleted_at is null
                        """)
                .params(tenantId, patientId, attachmentId)
                .query(AttachmentFileRow.class)
                .optional();
    }

    /** Exclusão lógica; devolve as linhas afetadas. */
    public int softDelete(UUID tenantId, UUID patientId, UUID attachmentId, UUID deletedBy) {
        return jdbc.sql("""
                        update attachments set deleted_at = now(), deleted_by = ?
                        where tenant_id = ? and patient_id = ? and id = ? and deleted_at is null
                        """)
                .params(deletedBy, tenantId, patientId, attachmentId)
                .update();
    }
}
