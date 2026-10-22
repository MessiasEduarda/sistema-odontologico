package br.com.odontosaas.clinic.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinic.repository.LogoRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClinicLogoService {

    static final int MAX_BYTES = 1024 * 1024;

    public record Logo(String contentType, byte[] data, Instant updatedAt) {}

    private final LogoRepository logos;
    private final AuditService audit;

    public ClinicLogoService(LogoRepository logos, AuditService audit) {
        this.logos = logos;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Optional<Logo> get() {
        return logos.find(TenantContext.requireTenantId())
                .map(row -> new Logo(row.contentType(), row.data(), row.updatedAt()));
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
        logos.upsert(tenantId, type, bytes, TenantContext.userId().orElse(null));
        audit.record(AuditAction.RECORD_UPDATED, "clinic_logo", tenantId, Map.of("bytes", bytes.length));
        return get().map(Logo::updatedAt).orElseThrow();
    }

    @Transactional
    public void delete() {
        UUID tenantId = TenantContext.requireTenantId();
        if (logos.delete(tenantId) > 0) {
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
