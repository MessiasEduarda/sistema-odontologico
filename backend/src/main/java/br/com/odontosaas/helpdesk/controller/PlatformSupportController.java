package br.com.odontosaas.helpdesk.controller;

import br.com.odontosaas.helpdesk.dto.request.CannedResponseRequest;
import br.com.odontosaas.helpdesk.dto.request.SupportReplyRequest;
import br.com.odontosaas.helpdesk.dto.request.TicketPriorityRequest;
import br.com.odontosaas.helpdesk.dto.request.TicketStatusRequest;
import br.com.odontosaas.helpdesk.dto.response.CannedResponseView;
import br.com.odontosaas.helpdesk.dto.response.PlatformTicketSummary;
import br.com.odontosaas.helpdesk.dto.response.SupportMetrics;
import br.com.odontosaas.helpdesk.dto.response.TicketDetail;
import br.com.odontosaas.helpdesk.dto.response.UnreadCount;
import br.com.odontosaas.helpdesk.service.SupportService;
import br.com.odontosaas.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Plataforma: suporte", description = "Fila de chamados, respostas, notas internas e respostas prontas")
public class PlatformSupportController {

    private static final String ADMIN = "hasRole('PLATFORM_ADMIN')";
    private static final String BASE = "/api/v1/platform/support";

    private final SupportService support;

    public PlatformSupportController(SupportService support) {
        this.support = support;
    }

    @GetMapping(BASE + "/tickets")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Fila de chamados de todas as clínicas")
    public List<PlatformTicketSummary> tickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID tenantId,
            @Parameter(description = "Texto contido no protocolo, no assunto ou no nome da clínica")
                    @RequestParam(required = false)
                    String searchTerm,
            @RequestParam(defaultValue = "false") boolean unread) {
        return support.platformTickets(status, priority, tenantId, searchTerm, unread);
    }

    @GetMapping(BASE + "/metrics")
    @PreAuthorize(ADMIN)
    public SupportMetrics metrics() {
        return support.metrics();
    }

    @GetMapping(BASE + "/unread")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Quantidade de mensagens das clínicas ainda não lidas pelo suporte")
    public UnreadCount unread() {
        return support.platformUnread();
    }

    @GetMapping(BASE + "/tickets/{id}")
    @PreAuthorize(ADMIN)
    public TicketDetail ticket(@PathVariable UUID id) {
        return support.platformDetail(id);
    }

    @PostMapping(BASE + "/tickets/{id}/messages")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Responde à clínica ou registra nota interna (a clínica não vê)")
    public TicketDetail reply(@PathVariable UUID id, @Valid @RequestBody SupportReplyRequest request) {
        return support.platformReply(id, request, admin());
    }

    @PostMapping(BASE + "/tickets/{id}/status")
    @PreAuthorize(ADMIN)
    public TicketDetail status(@PathVariable UUID id, @Valid @RequestBody TicketStatusRequest request) {
        return support.platformStatus(id, request.status(), admin());
    }

    @PutMapping(BASE + "/tickets/{id}/priority")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Muda a prioridade (o prazo de primeira resposta é recalculado)")
    public TicketDetail priority(@PathVariable UUID id, @Valid @RequestBody TicketPriorityRequest request) {
        return support.platformPriority(id, request.priority(), admin());
    }

    @PostMapping(value = BASE + "/tickets/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ADMIN)
    public TicketDetail attach(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return support.platformAttach(id, file, admin());
    }

    @GetMapping(BASE + "/tickets/{id}/attachments/{attachmentId}/download")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Baixa o anexo (auditado)")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return SupportFileResponse.of(support.platformDownload(id, attachmentId, admin()));
    }

    @GetMapping(BASE + "/canned-responses")
    @PreAuthorize(ADMIN)
    public List<CannedResponseView> cannedResponses(@RequestParam(defaultValue = "false") boolean active) {
        return support.cannedResponses(active);
    }

    @PostMapping(BASE + "/canned-responses")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public CannedResponseView createCannedResponse(@Valid @RequestBody CannedResponseRequest request) {
        return support.createCannedResponse(request);
    }

    @PutMapping(BASE + "/canned-responses/{id}")
    @PreAuthorize(ADMIN)
    public CannedResponseView updateCannedResponse(
            @PathVariable UUID id, @Valid @RequestBody CannedResponseRequest request) {
        return support.updateCannedResponse(id, request);
    }

    private static UUID admin() {
        return TenantContext.userId().orElseThrow();
    }
}
