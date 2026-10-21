package br.com.odontosaas.platform;

import br.com.odontosaas.identity.security.CurrentUser;
import br.com.odontosaas.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "Plataforma: clínicas", description = "Somente administradores da plataforma")
public class PlatformTenantController {

    private final PlatformTenantService service;

    public PlatformTenantController(PlatformTenantService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cria uma clínica",
            description = "Cria papéis padrão e envia convite ao responsável. 409 SLUG_TAKEN.")
    public PlatformDtos.TenantView create(@Valid @RequestBody PlatformDtos.CreateTenantRequest request) {
        return service.create(request, CurrentUser.require().userId());
    }

    public record ResendOwnerInviteRequest(
            @NotBlank @Email @Size(max = 254) String ownerEmail) {}

    @PostMapping("/{id}/owner-invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Reenvia o convite do responsável",
            description = "Gera um novo link (o anterior deixa de valer). 404 se a clínica não existir; "
                    + "409 ALREADY_MEMBER se a pessoa já aceitou.")
    public void resendOwnerInvite(@PathVariable UUID id, @Valid @RequestBody ResendOwnerInviteRequest request) {
        service.resendOwnerInvite(
                id, request.ownerEmail(), CurrentUser.require().userId());
    }

    @GetMapping
    @Operation(summary = "Lista clínicas (paginado)")
    public PageResponse<PlatformDtos.TenantView> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
}
