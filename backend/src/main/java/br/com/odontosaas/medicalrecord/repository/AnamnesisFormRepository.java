package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.request.Question;
import br.com.odontosaas.medicalrecord.dto.response.FormView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Acesso a dados das fichas de anamnese da clínica (tabela anamnesis_forms). */
@Repository
public class AnamnesisFormRepository {

    private static final TypeReference<List<Question>> QUESTIONS = new TypeReference<>() {};

    private static final String FORM_SELECT =
            "select id, name, kind, questions::text as questions, active, updated_at from anamnesis_forms";

    /** Quais fichas padrão a clínica já possui e se a harmonização orofacial está habilitada. */
    public record FormKindsRow(boolean dental, boolean aesthetic, boolean aestheticsEnabled) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AnamnesisFormRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private FormView mapForm(ResultSet rs) throws SQLException {
        return new FormView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("kind"),
                json.readValue(rs.getString("questions"), QUESTIONS),
                rs.getBoolean("active"),
                rs.getTimestamp("updated_at").toInstant());
    }

    public List<FormView> findAll(UUID tenantId, boolean includeInactive) {
        return jdbc.sql(FORM_SELECT + " where tenant_id = ?" + (includeInactive ? "" : " and active")
                        + " order by case kind when 'DENTAL' then 0 when 'AESTHETIC' then 1 else 2 end, name")
                .param(tenantId)
                .query((rs, n) -> mapForm(rs))
                .list();
    }

    public Optional<FormView> find(UUID tenantId, UUID formId) {
        return jdbc.sql(FORM_SELECT + " where tenant_id = ? and id = ?")
                .params(tenantId, formId)
                .query((rs, n) -> mapForm(rs))
                .optional();
    }

    public FormKindsRow findKinds(UUID tenantId) {
        return jdbc.sql("""
                        select exists(select 1 from anamnesis_forms where tenant_id = ? and kind = 'DENTAL') as dental,
                               exists(select 1 from anamnesis_forms where tenant_id = ? and kind = 'AESTHETIC')
                                   as aesthetic,
                               coalesce((select aesthetics_enabled from clinic_settings where tenant_id = ?), false)
                                   as aesthetics_enabled
                        """)
                .params(tenantId, tenantId, tenantId)
                .query(FormKindsRow.class)
                .single();
    }

    /** Insere a ficha padrão; ignora se já existir uma com o mesmo nome na clínica. */
    public void insertIfAbsent(
            UUID id, UUID tenantId, String name, String kind, List<Question> questions, UUID updatedBy) {
        jdbc.sql("""
                        insert into anamnesis_forms (id, tenant_id, name, kind, questions, updated_by)
                        values (?, ?, ?, ?, cast(? as jsonb), ?) on conflict (tenant_id, name) do nothing
                        """)
                .params(id, tenantId, name, kind, json.writeValueAsString(questions), updatedBy)
                .update();
    }

    public void insert(UUID id, UUID tenantId, String name, String kind, List<Question> questions, UUID updatedBy) {
        jdbc.sql("""
                        insert into anamnesis_forms (id, tenant_id, name, kind, questions, updated_by)
                        values (?, ?, ?, ?, cast(? as jsonb), ?)
                        """)
                .params(id, tenantId, name, kind, json.writeValueAsString(questions), updatedBy)
                .update();
    }

    public void update(UUID tenantId, UUID formId, String name, String kind, List<Question> questions, UUID updatedBy) {
        jdbc.sql("""
                        update anamnesis_forms set name = ?, kind = ?, questions = cast(? as jsonb), updated_by = ?,
                            updated_at = now() where tenant_id = ? and id = ?
                        """)
                .params(name, kind, json.writeValueAsString(questions), updatedBy, tenantId, formId)
                .update();
    }

    public void setActive(UUID tenantId, UUID formId, boolean active) {
        jdbc.sql("update anamnesis_forms set active = ?, updated_at = now() where tenant_id = ? and id = ?")
                .params(active, tenantId, formId)
                .update();
    }
}
