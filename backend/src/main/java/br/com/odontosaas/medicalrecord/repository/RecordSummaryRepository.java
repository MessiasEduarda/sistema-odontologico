package br.com.odontosaas.medicalrecord.repository;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de apoio ao resumo de abertura do prontuário (dados básicos e contagens por paciente). */
@Repository
public class RecordSummaryRepository {

    /** Dados básicos do paciente exibidos no resumo. */
    public record PatientBasicRow(String fullName, LocalDate birthDate) {}

    private final JdbcClient jdbc;

    public RecordSummaryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PatientBasicRow findPatientBasic(UUID tenantId, UUID patientId) {
        return jdbc.sql("select full_name, birth_date from patients where tenant_id = ? and id = ?")
                .params(tenantId, patientId)
                .query(PatientBasicRow.class)
                .single();
    }

    public int countNotes(UUID tenantId, UUID patientId) {
        return count("select count(*) from clinical_notes where tenant_id = ? and patient_id = ?", tenantId, patientId);
    }

    public int countPendingConsents(UUID tenantId, UUID patientId) {
        return count(
                "select count(*) from patient_consents where tenant_id = ? and patient_id = ? and status = 'PENDING'",
                tenantId,
                patientId);
    }

    public int countAttachments(UUID tenantId, UUID patientId) {
        return count(
                "select count(*) from attachments where tenant_id = ? and patient_id = ? and deleted_at is null",
                tenantId,
                patientId);
    }

    private int count(String sql, UUID tenantId, UUID patientId) {
        return jdbc.sql(sql).params(tenantId, patientId).query(Integer.class).single();
    }
}
