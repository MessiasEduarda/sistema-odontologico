package br.com.odontosaas.medicalrecord.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.medicalrecord.dto.response.AttachmentView;
import br.com.odontosaas.medicalrecord.repository.AttachmentRepository;
import br.com.odontosaas.medicalrecord.repository.AttachmentRepository.AttachmentFileRow;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final Set<String> CATEGORIES = Set.of("XRAY", "PHOTO", "EXAM", "DOCUMENT", "OTHER");

    private final AttachmentRepository attachments;
    private final TenantRefs refs;
    private final AuditService audit;
    private final Path root;

    public AttachmentService(
            AttachmentRepository attachments,
            TenantRefs refs,
            AuditService audit,
            @Value("${app.storage.path:./data/uploads}") String storagePath) {
        this.attachments = attachments;
        this.refs = refs;
        this.audit = audit;
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public record StoredFile(String filename, String contentType, Path path) {}

    @Transactional(readOnly = true)
    public List<AttachmentView> list(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        refs.require("patients", tenantId, patientId, "Paciente");
        return attachments.findByPatient(tenantId, patientId);
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
        attachments.insert(
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
                TenantContext.userId().orElse(null));
        audit.record(AuditAction.RECORD_CREATED, "attachment", id, Map.of("category", category));
        return list(patientId).stream()
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public StoredFile download(UUID patientId, UUID attachmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        AttachmentFileRow row = attachments
                .findFile(tenantId, patientId, attachmentId)
                .orElseThrow(() -> ApiException.notFound("Anexo"));
        Path path = resolve(row.storageKey());
        if (!Files.exists(path)) throw ApiException.notFound("Arquivo do anexo");
        audit.record(AuditAction.ATTACHMENT_DOWNLOADED, "attachment", attachmentId, Map.of());
        return new StoredFile(row.filename(), row.contentType(), path);
    }

    @Transactional
    public AttachmentView update(UUID patientId, UUID attachmentId, String category, String description) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!CATEGORIES.contains(category)) throw ApiException.invalidField("category", "Categoria inválida.");
        String text = description == null || description.isBlank() ? null : description.strip();
        if (attachments.updateDetails(tenantId, patientId, attachmentId, category, text) == 0) {
            throw ApiException.notFound("Anexo");
        }
        audit.record(AuditAction.RECORD_UPDATED, "attachment", attachmentId, Map.of("category", category));
        return list(patientId).stream()
                .filter(a -> a.id().equals(attachmentId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public AttachmentView setSharing(UUID patientId, UUID attachmentId, boolean shared) {
        UUID tenantId = TenantContext.requireTenantId();
        if (attachments.updateSharing(tenantId, patientId, attachmentId, shared) == 0) {
            throw ApiException.notFound("Anexo");
        }
        audit.record(AuditAction.RECORD_UPDATED, "attachment", attachmentId, Map.of("sharedWithPatient", shared));
        return list(patientId).stream()
                .filter(a -> a.id().equals(attachmentId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> listShared(UUID patientId) {
        return attachments.findSharedByPatient(TenantContext.requireTenantId(), patientId);
    }

    @Transactional
    public StoredFile downloadShared(UUID patientId, UUID attachmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        AttachmentFileRow row = attachments
                .findSharedFile(tenantId, patientId, attachmentId)
                .orElseThrow(() -> ApiException.notFound("Anexo"));
        Path path = resolve(row.storageKey());
        if (!Files.exists(path)) throw ApiException.notFound("Arquivo do anexo");
        audit.record(AuditAction.ATTACHMENT_DOWNLOADED, "attachment", attachmentId, Map.of("via", "portal"));
        return new StoredFile(row.filename(), row.contentType(), path);
    }

    @Transactional
    public void delete(UUID patientId, UUID attachmentId) {
        int updated = attachments.softDelete(
                TenantContext.requireTenantId(),
                patientId,
                attachmentId,
                TenantContext.userId().orElse(null));
        if (updated == 0) throw ApiException.notFound("Anexo");
        audit.record(AuditAction.RECORD_VOIDED, "attachment", attachmentId, Map.of());
    }

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

    Path root() {
        return root;
    }
}
