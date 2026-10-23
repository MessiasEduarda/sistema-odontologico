package br.com.odontosaas.portal.controller;

import br.com.odontosaas.portal.dto.response.PortalAccountStatus;
import br.com.odontosaas.portal.service.PortalAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Portal do paciente")
public class PortalAccountController {

    private static final String BASE = "/api/v1/patients/{patientId}/portal-account";

    private final PortalAccountService accounts;

    public PortalAccountController(PortalAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping(BASE)
    @PreAuthorize("hasAuthority('patients:read')")
    @Operation(summary = "Situação do acesso do paciente ao portal")
    public PortalAccountStatus status(@PathVariable UUID patientId) {
        return accounts.status(patientId);
    }

    @PostMapping(BASE + "/invite")
    @PreAuthorize("hasAuthority('patients:write')")
    @Operation(summary = "Envia (ou reenvia) o convite para o paciente criar a senha do portal")
    public PortalAccountStatus invite(@PathVariable UUID patientId) {
        return accounts.invite(patientId);
    }

    @PostMapping(BASE + "/block")
    @PreAuthorize("hasAuthority('patients:write')")
    @Operation(summary = "Bloqueia o acesso do paciente ao portal desta clínica (encerra as sessões)")
    public PortalAccountStatus block(@PathVariable UUID patientId) {
        return accounts.setBlocked(patientId, true);
    }

    @PostMapping(BASE + "/unblock")
    @PreAuthorize("hasAuthority('patients:write')")
    @Operation(summary = "Libera novamente o acesso do paciente ao portal")
    public PortalAccountStatus unblock(@PathVariable UUID patientId) {
        return accounts.setBlocked(patientId, false);
    }
}
