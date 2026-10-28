package br.com.odontosaas.helpdesk.controller;

import br.com.odontosaas.helpdesk.dto.request.NewTicketRequest;
import br.com.odontosaas.helpdesk.dto.request.TicketMessageRequest;
import br.com.odontosaas.helpdesk.dto.request.TicketRatingRequest;
import br.com.odontosaas.helpdesk.dto.response.SupportContact;
import br.com.odontosaas.helpdesk.dto.response.TicketDetail;
import br.com.odontosaas.helpdesk.dto.response.TicketSummary;
import br.com.odontosaas.helpdesk.dto.response.UnreadCount;
import br.com.odontosaas.helpdesk.service.SupportService;
import br.com.odontosaas.helpdesk.service.SupportService.Viewer;
import br.com.odontosaas.shared.Access;
import br.com.odontosaas.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "Suporte", description = "Chamados da clínica para a equipe da plataforma")
public class SupportController {

    static final String MANAGER_PERMISSION = "clinic:settings:manage";
    private static final String BASE = "/api/v1/support/tickets";

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    @GetMapping("/api/v1/public/support/contact")
    @Operation(summary = "Contato alternativo do suporte (para quem não consegue entrar)")
    public SupportContact contact() {
        return support.contact();
    }

    @GetMapping(BASE)
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Chamados da pessoa (ou de toda a clínica, para quem administra a clínica)")
    public List<TicketSummary> tickets(@RequestParam(required = false) String status, Authentication auth) {
        return support.clinicTickets(viewer(auth), status);
    }

    @PostMapping(BASE)
    @PreAuthorize(Access.MEMBER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre um chamado (até 10 por clínica a cada 24 horas)")
    public TicketDetail open(@Valid @RequestBody NewTicketRequest request, Authentication auth) {
        return support.open(request, viewer(auth));
    }

    @GetMapping("/api/v1/support/unread")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Quantidade de mensagens do suporte ainda não lidas")
    public UnreadCount unread(Authentication auth) {
        return support.unread(viewer(auth));
    }

    @GetMapping(BASE + "/{id}")
    @PreAuthorize(Access.MEMBER)
    public TicketDetail ticket(@PathVariable UUID id, Authentication auth) {
        return support.clinicDetail(id, viewer(auth));
    }

    @PostMapping(BASE + "/{id}/messages")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Responde no chamado (reabre se estava resolvido)")
    public TicketDetail reply(
            @PathVariable UUID id, @Valid @RequestBody TicketMessageRequest request, Authentication auth) {
        return support.clinicReply(id, request.body(), viewer(auth));
    }

    @PostMapping(BASE + "/{id}/resolve")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "A clínica informa que o problema foi resolvido")
    public TicketDetail resolve(@PathVariable UUID id, Authentication auth) {
        return support.clinicResolve(id, viewer(auth));
    }

    @PostMapping(BASE + "/{id}/rating")
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Avalia o atendimento (1 a 5) depois de resolvido")
    public TicketDetail rate(
            @PathVariable UUID id, @Valid @RequestBody TicketRatingRequest request, Authentication auth) {
        return support.rate(id, request.rating(), request.comment(), viewer(auth));
    }

    @PostMapping(value = BASE + "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Access.MEMBER)
    @Operation(summary = "Anexa print ou PDF (até 5 MB, até 6 por chamado)")
    public TicketDetail attach(@PathVariable UUID id, @RequestParam("file") MultipartFile file, Authentication auth) {
        return support.clinicAttach(id, file, viewer(auth));
    }

    @GetMapping(BASE + "/{id}/attachments/{attachmentId}/download")
    @PreAuthorize(Access.MEMBER)
    public ResponseEntity<Resource> download(
            @PathVariable UUID id, @PathVariable UUID attachmentId, Authentication auth) {
        return SupportFileResponse.of(support.clinicDownload(id, attachmentId, viewer(auth)));
    }

    static Viewer viewer(Authentication auth) {
        boolean manager = auth != null
                && auth.getAuthorities().stream().anyMatch(a -> MANAGER_PERMISSION.equals(a.getAuthority()));
        return new Viewer(TenantContext.userId().orElseThrow(), manager);
    }
}
