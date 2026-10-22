package br.com.odontosaas.medicalrecord.controller;

import br.com.odontosaas.medicalrecord.dto.response.AttachmentView;
import br.com.odontosaas.medicalrecord.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Prontuário")
public class AttachmentController {

    private static final String READ = "hasAuthority('clinical_records:read')";
    private static final String WRITE = "hasAuthority('clinical_records:write')";
    private static final String BASE = "/api/v1/patients/{patientId}";

    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping(BASE + "/attachments")
    @PreAuthorize(READ)
    public List<AttachmentView> attachments(@PathVariable UUID patientId) {
        return attachments.list(patientId);
    }

    @PostMapping(value = BASE + "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia anexo (PDF, JPG, PNG, WEBP; até 15 MB)")
    public AttachmentView upload(
            @PathVariable UUID patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(value = "description", required = false) String description) {
        return attachments.upload(patientId, file, category, description);
    }

    @GetMapping(BASE + "/attachments/{attachmentId}/download")
    @PreAuthorize(READ)
    @Operation(summary = "Baixa o anexo (auditado)")
    public ResponseEntity<Resource> download(@PathVariable UUID patientId, @PathVariable UUID attachmentId) {
        AttachmentService.StoredFile file = attachments.download(patientId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.filename(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(file.path()));
    }

    @DeleteMapping(BASE + "/attachments/{attachmentId}")
    @PreAuthorize(WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable UUID patientId, @PathVariable UUID attachmentId) {
        attachments.delete(patientId, attachmentId);
    }
}
