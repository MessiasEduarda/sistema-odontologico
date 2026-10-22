package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.response.ChartEntryView;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChartRepository {

    private final JdbcClient jdbc;

    public ChartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChartEntryView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select e.id, e.tooth, e.surfaces, e.condition, e.status, e.notes, u.name as created_by_name,
                               e.created_at, e.voided_at, e.void_reason
                        from dental_chart_entries e left join users u on u.id = e.created_by
                        where e.tenant_id = ? and e.patient_id = ? order by e.created_at
                        """)
                .params(tenantId, patientId)
                .query(ChartEntryView.class)
                .list();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            int tooth,
            String surfaces,
            String condition,
            String status,
            String notes,
            UUID createdBy) {
        jdbc.sql("""
                        insert into dental_chart_entries (id, tenant_id, patient_id, tooth, surfaces, condition, status,
                            notes, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, patientId, tooth, surfaces, condition, status, notes, createdBy)
                .update();
    }

    public int voidEntry(UUID tenantId, UUID patientId, UUID entryId, UUID voidedBy, String reason) {
        return jdbc.sql("""
                        update dental_chart_entries set voided_at = now(), voided_by = ?, void_reason = ?
                        where tenant_id = ? and patient_id = ? and id = ? and voided_at is null
                        """)
                .params(voidedBy, reason, tenantId, patientId, entryId)
                .update();
    }
}
