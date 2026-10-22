package br.com.odontosaas.treatment.repository;

import br.com.odontosaas.treatment.dto.response.PlanSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TreatmentPlanRepository {

    private final JdbcClient jdbc;

    public TreatmentPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlanSummary> list(UUID tenantId, UUID patientId, String status) {
        return jdbc.sql("""
                        select t.id, t.patient_id, p.full_name as patient_name, t.professional_id,
                               pr.name as professional_name, t.title, t.status,
                               greatest(coalesce(sum(case when i.status <> 'CANCELLED'
                                   then i.quantity * i.unit_price - i.discount end), 0) - t.discount, 0) as total,
                               count(i.id) filter (where i.status <> 'CANCELLED') as items_total,
                               count(i.id) filter (where i.status = 'DONE') as items_done,
                               t.created_at, t.approved_at
                        from treatment_plans t
                        join patients p on p.tenant_id = t.tenant_id and p.id = t.patient_id
                        join professionals pr on pr.tenant_id = t.tenant_id and pr.id = t.professional_id
                        left join treatment_plan_items i on i.tenant_id = t.tenant_id and i.plan_id = t.id
                        where t.tenant_id = ? and (cast(? as uuid) is null or t.patient_id = ?)
                          and (cast(? as text) is null or t.status = ?)
                        group by t.id, p.full_name, pr.name
                        order by t.created_at desc limit 300
                        """)
                .params(tenantId, patientId, patientId, status, status)
                .query(PlanSummary.class)
                .list();
    }

    public Optional<TreatmentPlanHeadRow> findHead(UUID tenantId, UUID id) {
        return jdbc.sql("""
                        select t.id, t.patient_id, p.full_name as patient_name, t.professional_id,
                               pr.name as professional_name, t.title, t.status, t.discount, t.payment_conditions,
                               t.notes, t.valid_until, t.approved_at, t.approved_by_name, t.created_at, t.version,
                               (select r.id from receivables r where r.tenant_id = t.tenant_id
                                   and r.treatment_plan_id = t.id and r.status <> 'CANCELLED' limit 1)
                                   as receivable_id
                        from treatment_plans t
                        join patients p on p.tenant_id = t.tenant_id and p.id = t.patient_id
                        join professionals pr on pr.tenant_id = t.tenant_id and pr.id = t.professional_id
                        where t.tenant_id = ? and t.id = ?
                        """)
                .params(tenantId, id)
                .query(TreatmentPlanHeadRow.class)
                .optional();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID professionalId,
            String title,
            BigDecimal discount,
            String paymentConditions,
            String notes,
            LocalDate validUntil,
            UUID createdBy) {
        jdbc.sql("""
                        insert into treatment_plans (id, tenant_id, patient_id, professional_id, title, discount,
                            payment_conditions, notes, valid_until, created_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        professionalId,
                        title,
                        discount,
                        paymentConditions,
                        notes,
                        validUntil,
                        createdBy)
                .update();
    }

    public int update(
            UUID professionalId,
            String title,
            BigDecimal discount,
            String paymentConditions,
            String notes,
            LocalDate validUntil,
            UUID tenantId,
            UUID id,
            Long version) {
        return jdbc.sql("""
                        update treatment_plans set professional_id = ?, title = ?, discount = ?,
                            payment_conditions = ?, notes = ?, valid_until = ?, updated_at = now(),
                            version = version + 1
                        where tenant_id = ? and id = ? and (cast(? as bigint) is null or version = ?)
                        """)
                .params(
                        professionalId,
                        title,
                        discount,
                        paymentConditions,
                        notes,
                        validUntil,
                        tenantId,
                        id,
                        version,
                        version)
                .update();
    }

    public int approve(String approvedByName, UUID tenantId, UUID id, long version) {
        return jdbc.sql("""
                        update treatment_plans set status = 'APPROVED', approved_at = now(), approved_by_name = ?,
                            updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ? and version = ?
                        """).params(approvedByName, tenantId, id, version).update();
    }

    public void refreshCompletion(UUID tenantId, UUID planId) {
        jdbc.sql("""
                        update treatment_plans t set status = case
                                when not exists (select 1 from treatment_plan_items i where i.tenant_id = t.tenant_id
                                    and i.plan_id = t.id and i.status = 'PLANNED') then 'COMPLETED' else 'APPROVED' end,
                            updated_at = now(), version = version + 1
                        where t.tenant_id = ? and t.id = ? and t.status in ('APPROVED', 'COMPLETED')
                        """).params(tenantId, planId).update();
    }

    public void transition(String to, String reason, String reasonNote, UUID tenantId, UUID id) {
        jdbc.sql("""
                        update treatment_plans set status = ?, updated_at = now(), version = version + 1,
                            notes = case when cast(? as text) is null then notes
                                else concat_ws(E'\\n', notes, cast(? as text)) end
                        where tenant_id = ? and id = ?
                        """).params(to, reason, reasonNote, tenantId, id).update();
    }
}
