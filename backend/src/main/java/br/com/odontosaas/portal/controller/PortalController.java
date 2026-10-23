package br.com.odontosaas.portal.controller;

import br.com.odontosaas.medicalrecord.service.AttachmentService;
import br.com.odontosaas.portal.dto.request.ContactBody;
import br.com.odontosaas.portal.dto.request.RespondBody;
import br.com.odontosaas.portal.dto.request.SignBody;
import br.com.odontosaas.portal.dto.response.PortalOverview;
import br.com.odontosaas.portal.dto.response.PortalRecords;
import br.com.odontosaas.portal.service.PortalAuthService;
import br.com.odontosaas.portal.service.PortalService;
import br.com.odontosaas.portal.support.PortalCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Portal do paciente")
public class PortalController {

    private static final String BASE = "/api/v1/public/portal";

    private final PortalService service;
    private final PortalAuthService auth;

    public PortalController(PortalService service, PortalAuthService auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping(BASE + "/overview")
    @Operation(summary = "Paciente logado: consultas, termos pendentes e contato", tags = "Público")
    public PortalOverview overview(@CookieValue(name = PortalCookies.SESSION, required = false) String session) {
        return service.overview(auth.requireProfile(session));
    }

    @GetMapping(BASE + "/records")
    @Operation(
            summary = "Paciente logado: documentos, termos assinados, orçamentos, recibos e anexos liberados",
            tags = "Público")
    public PortalRecords records(@CookieValue(name = PortalCookies.SESSION, required = false) String session) {
        return service.records(auth.requireProfile(session));
    }

    @GetMapping(BASE + "/attachments/{attachmentId}/download")
    @Operation(summary = "Paciente logado: baixa um anexo liberado (auditado)", tags = "Público")
    public ResponseEntity<Resource> attachment(
            @CookieValue(name = PortalCookies.SESSION, required = false) String session,
            @PathVariable UUID attachmentId) {
        AttachmentService.StoredFile file = service.attachment(auth.requireProfile(session), attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(file.path()));
    }

    @PostMapping(BASE + "/appointments/respond")
    @Operation(summary = "Paciente logado: confirmar/cancelar consulta", tags = "Público")
    public PortalOverview respond(
            @CookieValue(name = PortalCookies.SESSION, required = false) String session,
            @Valid @RequestBody RespondBody body) {
        return service.respond(auth.requireProfile(session), body);
    }

    @PostMapping(BASE + "/consents/sign")
    @Operation(summary = "Paciente logado: assinar termo pendente", tags = "Público")
    public PortalOverview sign(
            @CookieValue(name = PortalCookies.SESSION, required = false) String session,
            @Valid @RequestBody SignBody body) {
        return service.sign(auth.requireProfile(session), body);
    }

    @PostMapping(BASE + "/contact")
    @Operation(summary = "Paciente logado: atualizar celular, telefone e preferências", tags = "Público")
    public PortalOverview contact(
            @CookieValue(name = PortalCookies.SESSION, required = false) String session,
            @Valid @RequestBody ContactBody body) {
        return service.updateContact(auth.requireProfile(session), body);
    }
}
