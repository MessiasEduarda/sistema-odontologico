package br.com.odontosaas.clinical;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinical.ClinicalDtos.AttachmentView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Anexos do prontuário (radiografias, fotos, exames, documentos). Arquivos ficam em disco fora da pasta pública,
 * organizados por clínica, com nome aleatório; o download só acontece pela API, com permissão e auditoria.
 *
 * <p>O tipo é conferido pelo CONTEÚDO do arquivo (assinatura binária), não pela extensão enviada.
 */
@Service
public class AttachmentService {

    static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final Set<String> CATEGORIES = Set.of("XRAY", "PHOTO", "EXAM", "DOCUMENT", "OTHER");

    private final JdbcClient jdbc;
    private final TenantRefs refs;
    private final AuditService audit;
    private final Path root;

    public AttachmentService(
            JdbcClient jdbc,
            TenantRefs refs,
            AuditService audit,
            @Value("${app.storage.path:./data/uploads}") String storagePath) {
        this.jdbc = jdbc;
        this.refs = refs;
        this.audit = audit;
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public record StoredFile(String filename, String contentType, Path path) {}

    @Transactional(readOnly = true)
    public List<AttachmentView> list(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        return jdbc.sql("""
                        select a.id, a.category, a.description, a.filename, a.content_type, a.size_bytes,
                               u.name as uploaded_by_name, a.created_at
                        from attachments a left join users u on u.id = a.uploaded_by
                        where a.tenant_id = ? and a.patient_id = ? and a.deleted_at is null
                        order by a.created_at desc
                        """)
                .params(tenantId, patientId)
                .query(AttachmentView.class)
                .list();
    }

    @Transactional
    public AttachmentView upload(UUID patientId, MultipartFile file, String category, String description) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        if (file == null || file.isEmpty()) throw ApiException.invalidField("file", "Selecione um arquivo.");
        if (file.getSize() > MAX_BYTES) throw ApiException.invalidField("file", "O arquivo pode ter no máximo 15 MB.");
        if (!CATEGORIES.contains(category)) throw ApiException.invalidField("category", "Categoria inválida.");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String contentType = detectType(bytes);
        if (contentType == null) {
            throw ApiException.invalidField("file", "Formato não permitido. Envie PDF, JPG, PNG ou WEBP.");
        }

        UUID id = Ids.newId();
        LocalDate today = LocalDate.now();
        String key = "%s/%d/%02d/%s".formatted(tenantId, today.getYear(), today.getMonthValue(), id);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar anexo", e);
        }
        String filename = sanitize(file.getOriginalFilename());
        jdbc.sql("""
                        insert into attachments (id, tenant_id, patient_id, category, description, filename,
                            content_type, size_bytes, sha256, storage_key, uploaded_by)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        category,
                        description == null || description.isBlank() ? null : description.strip(),
                        filename,
                        contentType,
                        (long) bytes.length,
                        sha256(bytes),
                        key,
                        TenantContext.userId().orElse(null))
                .update();
        audit.record(AuditAction.RECORD_CREATED, "attachment", id, Map.of("category", category));
        return list(patientId).stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public StoredFile download(UUID patientId, UUID attachmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        record Row(String filename, String contentType, String storageKey) {}
        Row row = jdbc.sql("""
                        select filename, content_type, storage_key from attachments
                        where tenant_id = ? and patient_id = ? and id = ? and deleted_at is null
                        """)
                .params(tenantId, patientId, attachmentId)
                .query(Row.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Anexo"));
        Path path = resolve(row.storageKey());
        if (!Files.exists(path)) throw ApiException.notFound("Arquivo do anexo");
        audit.record(AuditAction.ATTACHMENT_DOWNLOADED, "attachment", attachmentId, Map.of());
        return new StoredFile(row.filename(), row.contentType(), path);
    }

    /** Exclusão lógica: o arquivo é mantido (retenção legal do prontuário) e some das listagens. */
    @Transactional
    public void delete(UUID patientId, UUID attachmentId) {
        int updated = jdbc.sql("""
                        update attachments set deleted_at = now(), deleted_by = ?
                        where tenant_id = ? and patient_id = ? and id = ? and deleted_at is null
                        """)
                .params(TenantContext.userId().orElse(null), TenantContext.requireTenantId(), patientId, attachmentId)
                .update();
        if (updated == 0) throw ApiException.notFound("Anexo");
        audit.record(AuditAction.RECORD_VOIDED, "attachment", attachmentId, Map.of());
    }

    /** Impede path traversal: a chave sempre resolve para dentro da pasta raiz. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalStateException("Chave de armazenamento inválida");
        return path;
    }

    static String detectType(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
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

    private static String sanitize(String original) {
        String name =
                original == null ? "arquivo" : Path.of(original).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Para testes/diagnóstico. */
    Path root() {
        return root;
    }
}
