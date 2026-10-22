package br.com.odontosaas.communication.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MessageTemplateRepository {

    public record TemplateRow(String type, String subject, String body, boolean active, boolean custom) {}

    private final JdbcClient jdbc;

    public MessageTemplateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TemplateRow> find(UUID tenantId, String type) {
        return jdbc.sql("select type, subject, body, active, true as custom from message_templates"
                        + " where tenant_id = ? and type = ?")
                .params(tenantId, type)
                .query(TemplateRow.class)
                .optional();
    }

    public void upsert(UUID tenantId, String type, String subject, String body, Boolean active) {
        jdbc.sql("""
                        insert into message_templates (tenant_id, type, subject, body, active)
                        values (?, ?, ?, ?, coalesce(cast(? as boolean), true))
                        on conflict (tenant_id, type) do update set subject = excluded.subject, body = excluded.body,
                            active = coalesce(cast(? as boolean), message_templates.active), updated_at = now()
                        """).params(tenantId, type, subject, body, active, active).update();
    }
}
