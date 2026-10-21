package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.response.CareTemplateView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados dos modelos de orientação ao paciente (tabela care_instruction_templates). */
@Repository
public class CareTemplateRepository {

    private static final String SELECT =
            "select id, title, category, moment, body, active, updated_at from care_instruction_templates";

    private final JdbcClient jdbc;

    public CareTemplateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CareTemplateView> findAll(UUID tenantId, boolean includeInactive) {
        return jdbc.sql(SELECT + " where tenant_id = ?" + (includeInactive ? "" : " and active")
                        + " order by category, moment, title")
                .param(tenantId)
                .query(CareTemplateView.class)
                .list();
    }

    public Optional<CareTemplateView> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(CareTemplateView.class)
                .optional();
    }

    public void insert(
            UUID id,
            UUID tenantId,
            String title,
            String category,
            String moment,
            String body,
            boolean active,
            UUID createdBy) {
        jdbc.sql("""
                        insert into care_instruction_templates (id, tenant_id, title, category, moment, body, active,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, title, category, moment, body, active, createdBy)
                .update();
    }

    public void update(
            UUID tenantId, UUID id, String title, String category, String moment, String body, Boolean active) {
        jdbc.sql("""
                        update care_instruction_templates set title = ?, category = ?, moment = ?, body = ?,
                            active = coalesce(cast(? as boolean), active), updated_at = now() where tenant_id = ? and id = ?
                        """)
                .params(title, category, moment, body, active, tenantId, id)
                .update();
    }
}
