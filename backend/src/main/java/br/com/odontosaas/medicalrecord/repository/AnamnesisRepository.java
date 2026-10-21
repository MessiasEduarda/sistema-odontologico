package br.com.odontosaas.medicalrecord.repository;

import br.com.odontosaas.medicalrecord.dto.request.Answer;
import br.com.odontosaas.medicalrecord.dto.request.Question;
import br.com.odontosaas.medicalrecord.dto.response.Alert;
import br.com.odontosaas.medicalrecord.dto.response.AnamnesisView;
import br.com.odontosaas.medicalrecord.dto.response.RecentAnamnesis;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Acesso a dados dos preenchimentos de anamnese (tabela anamneses). */
@Repository
public class AnamnesisRepository {

    private static final TypeReference<List<Question>> QUESTIONS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Answer>> ANSWERS = new TypeReference<>() {};
    private static final TypeReference<List<Alert>> ALERTS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AnamnesisRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID formId,
            String formName,
            String formKind,
            List<Question> questions,
            Map<String, Answer> answers,
            List<Alert> alerts,
            String notes,
            String filledBy,
            UUID createdBy) {
        jdbc.sql("""
                        insert into anamneses (id, tenant_id, patient_id, form_id, form_name, form_kind, questions,
                            answers, alerts, notes, filled_by, created_by)
                        values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        formId,
                        formName,
                        formKind,
                        json.writeValueAsString(questions),
                        json.writeValueAsString(answers),
                        json.writeValueAsString(alerts),
                        notes,
                        filledBy,
                        createdBy)
                .update();
    }

    public List<AnamnesisView> findByPatient(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select a.id, a.form_id, a.form_name, a.form_kind, a.questions::text as questions,
                               a.answers::text as answers, a.alerts::text as alerts, a.notes, a.filled_by,
                               u.name as created_by_name, a.created_at
                        from anamneses a left join users u on u.id = a.created_by
                        where a.tenant_id = ? and a.patient_id = ? order by a.created_at desc
                        """)
                .params(tenantId, patientId)
                .query((rs, n) -> new AnamnesisView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("form_id", UUID.class),
                        rs.getString("form_name"),
                        rs.getString("form_kind"),
                        json.readValue(rs.getString("questions"), QUESTIONS),
                        json.readValue(rs.getString("answers"), ANSWERS),
                        json.readValue(rs.getString("alerts"), ALERTS),
                        rs.getString("notes"),
                        rs.getString("filled_by"),
                        rs.getString("created_by_name"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** Preenchimentos recentes da clínica; filtros nulos são ignorados. */
    public List<RecentAnamnesis> findRecent(UUID tenantId, String kind, boolean alertsOnly, String query) {
        return jdbc.sql("""
                        select a.id, a.patient_id, p.full_name as patient_name, a.form_name, a.form_kind,
                               a.alerts::text as alerts, a.filled_by, u.name as created_by_name, a.created_at
                        from anamneses a
                        join patients p on p.tenant_id = a.tenant_id and p.id = a.patient_id
                        left join users u on u.id = a.created_by
                        where a.tenant_id = :tenant
                          and (cast(:kind as varchar) is null or a.form_kind = :kind)
                          and (not :alertsOnly or jsonb_array_length(a.alerts) > 0)
                          and (cast(:q as varchar) is null or lower(p.full_name) like :q)
                        order by a.created_at desc limit 200
                        """)
                .param("tenant", tenantId)
                .param("kind", kind)
                .param("alertsOnly", alertsOnly)
                .param("q", query)
                .query((rs, n) -> new RecentAnamnesis(
                        rs.getObject("id", UUID.class),
                        rs.getObject("patient_id", UUID.class),
                        rs.getString("patient_name"),
                        rs.getString("form_name"),
                        rs.getString("form_kind"),
                        json.readValue(rs.getString("alerts"), ALERTS),
                        rs.getString("filled_by"),
                        rs.getString("created_by_name"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
