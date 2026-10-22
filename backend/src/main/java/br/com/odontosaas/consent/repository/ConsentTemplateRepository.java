package br.com.odontosaas.consent.repository;

import br.com.odontosaas.consent.dto.response.ConsentTemplateView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConsentTemplateRepository {

    private static final String TEMPLATE_SELECT =
            "select id, family_id, title, category, body, version, active, created_at from consent_templates";

    private final JdbcClient jdbc;

    public ConsentTemplateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ConsentTemplateView> findAll(UUID tenantId, boolean includeOldVersions) {
        return jdbc.sql(TEMPLATE_SELECT + " where tenant_id = ?" + (includeOldVersions ? "" : " and active")
                        + " order by category, title, version desc")
                .param(tenantId)
                .query(ConsentTemplateView.class)
                .list();
    }

    public Optional<ConsentTemplateView> find(UUID tenantId, UUID id) {
        return jdbc.sql(TEMPLATE_SELECT + " where tenant_id = ? and id = ?")
                .params(tenantId, id)
                .query(ConsentTemplateView.class)
                .optional();
    }

    public void insertFirstVersion(UUID id, UUID tenantId, String title, String category, String body, UUID createdBy) {
        jdbc.sql("""
                        insert into consent_templates (id, tenant_id, family_id, title, category, body, version,
                            created_by) values (?, ?, ?, ?, ?, ?, 1, ?)
                        """).params(id, tenantId, id, title, category, body, createdBy).update();
    }

    public void insertVersion(
            UUID id,
            UUID tenantId,
            UUID familyId,
            String title,
            String category,
            String body,
            int version,
            UUID createdBy) {
        jdbc.sql("""
                        insert into consent_templates (id, tenant_id, family_id, title, category, body, version,
                            created_by) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, familyId, title, category, body, version, createdBy)
                .update();
    }

    public void deactivateFamily(UUID tenantId, UUID familyId) {
        jdbc.sql("update consent_templates set active = false where tenant_id = ? and family_id = ?")
                .params(tenantId, familyId)
                .update();
    }

    public boolean existsByTitle(UUID tenantId, String title) {
        return jdbc.sql("select exists(select 1 from consent_templates where tenant_id = ? and title = ?)")
                .params(tenantId, title)
                .query(Boolean.class)
                .single();
    }
}
