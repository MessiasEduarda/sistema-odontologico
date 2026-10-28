package br.com.odontosaas.helpdesk.service;

import br.com.odontosaas.shared.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class SupportFiles {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path root;

    public SupportFiles(@Value("${app.storage.path:./data/uploads}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize().resolve("support");
    }

    public record Upload(String key, String filename, String contentType, long size) {}

    public Upload store(UUID tenantId, UUID attachmentId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.invalidField("file", "Escolha o arquivo a enviar.");
        if (file.getSize() > MAX_BYTES) throw ApiException.invalidField("file", "O arquivo pode ter no máximo 5 MB.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String contentType = detectType(bytes);
        if (contentType == null) {
            throw ApiException.invalidField("file", "Formato não permitido. Envie um print (PNG, JPG ou WEBP) ou PDF.");
        }
        LocalDate today = LocalDate.now();
        String key = "%s/%d/%02d/%s".formatted(tenantId, today.getYear(), today.getMonthValue(), attachmentId);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar anexo do suporte", e);
        }
        return new Upload(key, sanitize(file.getOriginalFilename()), contentType, bytes.length);
    }

    public Path path(String key) {
        Path path = resolve(key);
        if (!Files.exists(path)) throw ApiException.notFound("Arquivo do anexo");
        return path;
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao apagar anexo do suporte", e);
        }
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
}
