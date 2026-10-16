package br.com.odontosaas.portal;

import br.com.odontosaas.portal.PortalService.AccessView;
import br.com.odontosaas.portal.PortalService.ContactBody;
import br.com.odontosaas.portal.PortalService.PortalOverview;
import br.com.odontosaas.portal.PortalService.RespondBody;
import br.com.odontosaas.portal.PortalService.SignBody;
import br.com.odontosaas.portal.PortalService.TokenBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Portal do paciente")
public class PortalController {

    private final PortalService service;

    public PortalController(PortalService service) {
        this.service = service;
    }

    public record GrantRequest(boolean sendEmail) {}

    @PostMapping("/api/v1/patients/{patientId}/portal-access")
    @PreAuthorize("hasAuthority('patients:write')")
    @Operation(summary = "Gera link do portal (30 dias); revoga links anteriores")
    public AccessView grant(@PathVariable UUID patientId, @RequestBody(required = false) GrantRequest request) {
        return service.grant(patientId, request != null && request.sendEmail());
    }

    @PostMapping("/api/v1/patients/{patientId}/portal-access/revoke")
    @PreAuthorize("hasAuthority('patients:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID patientId) {
        service.revoke(patientId);
    }

    @PostMapping("/api/v1/public/portal/overview")
    @Operation(summary = "Público: dados do portal", tags = "Público")
    public PortalOverview overview(@Valid @RequestBody TokenBody body) {
        return service.overview(body.token());
    }

    @PostMapping("/api/v1/public/portal/appointments/respond")
    @Operation(summary = "Público: confirmar/cancelar consulta", tags = "Público")
    public PortalOverview respond(@Valid @RequestBody RespondBody body) {
        return service.respond(body);
    }

    @PostMapping("/api/v1/public/portal/consents/sign")
    @Operation(summary = "Público: assinar termo pendente", tags = "Público")
    public PortalOverview sign(@Valid @RequestBody SignBody body) {
        return service.sign(body);
    }

    @PostMapping("/api/v1/public/portal/contact")
    @Operation(summary = "Público: atualizar contato e preferências", tags = "Público")
    public PortalOverview contact(@Valid @RequestBody ContactBody body) {
        return service.updateContact(body);
    }
}
