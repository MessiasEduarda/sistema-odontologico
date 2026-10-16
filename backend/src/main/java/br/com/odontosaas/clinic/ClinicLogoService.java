package br.com.odontosaas.clinic;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Logo da clínica (menu lateral e PDFs). Aceita somente PNG de até 1 MB, com o tipo verificado pelo conteúdo do
 * arquivo (não pela extensão). SVG não é aceito porque pode conter scripts.
 */
@Service
public class ClinicLogoService {

    static final int MAX_BYTES = 1024 * 1024;

    public record Logo(String contentType, byte[] data, Instant updatedAt) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public ClinicLogoService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Optional<Logo> get() {
        return jdbc.sql("select content_type, data, updated_at from clinic_logos where tenant_id = ?")
                .param(TenantContext.requireTenantId())
                .query((rs, n) -> new Logo(
                        rs.getString("content_type"),
                        rs.getBytes("data"),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    @Transactional
    public Instant save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.invalidField("file", "Envie a imagem da logo.");
        if (file.getSize() > MAX_BYTES) throw ApiException.invalidField("file", "A logo deve ter no máximo 1 MB.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.invalidField("file", "Não foi possível ler o arquivo.");
        }
        String type = detectImage(bytes);
        if (!"image/png".equals(type)) throw ApiException.invalidField("file", "Envie a logo em PNG.");
        UUID tenantId = TenantContext.requireTenantId();
        jdbc.sql("""
                        insert into clinic_logos (tenant_id, content_type, data, updated_by) values (?, ?, ?, ?)
                        on conflict (tenant_id) do update set content_type = excluded.content_type,
                            data = excluded.data, updated_by = excluded.updated_by, updated_at = now()
                        """)
                .params(tenantId, type, bytes, TenantContext.userId().orElse(null))
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "clinic_logo", tenantId, Map.of("bytes", bytes.length));
        return get().map(Logo::updatedAt).orElseThrow();
    }

    @Transactional
    public void delete() {
        UUID tenantId = TenantContext.requireTenantId();
        if (jdbc.sql("delete from clinic_logos where tenant_id = ?")
                        .param(tenantId)
                        .update()
                > 0) {
            audit.record(AuditAction.RECORD_DEACTIVATED, "clinic_logo", tenantId, Map.of());
        }
    }

    static String detectImage(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P') return "image/webp";
        return null;
    }
}
