package br.com.odontosaas.clinic.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LogoRepository {

    public record LogoRow(String contentType, byte[] data, Instant updatedAt) {}

    private final JdbcClient jdbc;

    public LogoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LogoRow> find(UUID tenantId) {
        return jdbc.sql("select content_type, data, updated_at from clinic_logos where tenant_id = ?")
                .param(tenantId)
                .query((rs, n) -> new LogoRow(
                        rs.getString("content_type"),
                        rs.getBytes("data"),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    public void upsert(UUID tenantId, String contentType, byte[] data, UUID updatedBy) {
        jdbc.sql("""
                        insert into clinic_logos (tenant_id, content_type, data, updated_by) values (?, ?, ?, ?)
                        on conflict (tenant_id) do update set content_type = excluded.content_type,
                            data = excluded.data, updated_by = excluded.updated_by, updated_at = now()
                        """).params(tenantId, contentType, data, updatedBy).update();
    }

    public int delete(UUID tenantId) {
        return jdbc.sql("delete from clinic_logos where tenant_id = ?")
                .param(tenantId)
                .update();
    }
}
