package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.response.PerformedView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados dos procedimentos realizados (tabela performed_procedures). */
@Repository
public class PerformedProcedureRepository {

    /** Resultado da anulação: item do plano de tratamento vinculado (pode ser nulo). */
    public record VoidedRow(UUID treatmentItemId) {}

    private final JdbcClient jdbc;

    public PerformedProcedureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PerformedView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select x.id, x.professional_id, p.name as professional_name, x.procedure_id, x.description,
                               x.tooth, x.surfaces, x.treatment_item_id, x.performed_at, x.notes, x.voided_at,
                               x.void_reason
                        from performed_procedures x
                        join professionals p on p.tenant_id = x.tenant_id and p.id = x.professional_id
                        where x.tenant_id = ? and x.patient_id = ? order by x.performed_at desc
                        """)
                .params(tenantId, patientId)
                .query(PerformedView.class)
                .list();
    }

    /** Indica se o item do plano pertence ao paciente, está planejado e o plano está aprovado. */
    public boolean isApprovedPlannedItem(UUID tenantId, UUID treatmentItemId, UUID patientId) {
        return jdbc.sql("""
                        select exists(select 1 from treatment_plan_items i
                            join treatment_plans t on t.tenant_id = i.tenant_id and t.id = i.plan_id
                            where i.tenant_id = ? and i.id = ? and t.patient_id = ? and i.status = 'PLANNED'
                              and t.status in ('APPROVED'))
                        """)
                .params(tenantId, treatmentItemId, patientId)
                .query(Boolean.class)
                .single();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID professionalId,
            UUID procedureId,
            String description,
            Integer tooth,
            String surfaces,
            UUID treatmentItemId,
            Instant performedAt,
            String notes,
            UUID createdBy) {
        jdbc.sql("""
                        insert into performed_procedures (id, tenant_id, patient_id, professional_id, procedure_id,
                            description, tooth, surfaces, treatment_item_id, performed_at, notes, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        professionalId,
                        procedureId,
                        description,
                        tooth,
                        surfaces,
                        treatmentItemId,
                        Timestamp.from(performedAt),
                        notes,
                        createdBy)
                .update();
    }

    /** Anula o procedimento (exclusão lógica); vazio se não existe ou já estava anulado. */
    public Optional<VoidedRow> voidReturningItem(
            UUID tenantId, UUID patientId, UUID performedId, UUID voidedBy, String reason) {
        return jdbc.sql("""
                        update performed_procedures set voided_at = now(), voided_by = ?, void_reason = ?
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        returning treatment_item_id
                        """)
                .params(voidedBy, reason, tenantId, patientId, performedId)
                .query((rs, n) -> new VoidedRow(rs.getObject(1, UUID.class)))
                .optional();
    }
}
