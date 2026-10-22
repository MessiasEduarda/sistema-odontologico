package br.com.odontosaas.identity.controller;

import br.com.odontosaas.identity.dto.request.CreateInvitationRequest;
import br.com.odontosaas.identity.dto.request.UpdateMemberRolesRequest;
import br.com.odontosaas.identity.dto.response.InvitationView;
import br.com.odontosaas.identity.dto.response.MemberView;
import br.com.odontosaas.identity.dto.response.RoleView;
import br.com.odontosaas.identity.security.CurrentUser;
import br.com.odontosaas.identity.service.InvitationService;
import br.com.odontosaas.identity.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinic")
@Tag(name = "Equipe da clínica", description = "Membros, papéis e convites da clínica ativa")
public class ClinicAccessController {

    private final MemberService members;
    private final InvitationService invitations;

    public ClinicAccessController(MemberService members, InvitationService invitations) {
        this.members = members;
        this.invitations = invitations;
    }

    @GetMapping("/members")
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "Lista a equipe", description = "Permissão: users:read")
    public List<MemberView> listMembers() {
        return members.list();
    }

    @PutMapping("/members/{membershipId}/roles")
    @PreAuthorize("hasAuthority('users:manage')")
    @Operation(
            summary = "Define os papéis de um membro",
            description = "Permissão: users:manage. 422 LAST_OWNER se removeria o último administrador.")
    public MemberView updateRoles(
            @PathVariable UUID membershipId, @Valid @RequestBody UpdateMemberRolesRequest request) {
        return members.updateRoles(membershipId, request.roleCodes());
    }

    @PostMapping("/members/{membershipId}/deactivate")
    @PreAuthorize("hasAuthority('users:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativa um membro", description = "Permissão: users:manage. Encerra as sessões dele.")
    public void deactivate(@PathVariable UUID membershipId) {
        members.deactivate(membershipId, CurrentUser.require());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "Papéis e permissões da clínica", description = "Permissão: users:read")
    public List<RoleView> roles() {
        return members.roles();
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('users:manage')")
    @Operation(summary = "Convites pendentes", description = "Permissão: users:manage")
    public List<InvitationView> listInvitations() {
        return invitations.listPending();
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasAuthority('users:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Convida uma pessoa por e-mail",
            description = "Permissão: users:manage. 409 ALREADY_MEMBER. Substitui convite pendente anterior.")
    public InvitationView invite(@Valid @RequestBody CreateInvitationRequest request) {
        return invitations.invite(
                request.email(), request.roleCode(), CurrentUser.require().userId());
    }

    @DeleteMapping("/invitations/{id}")
    @PreAuthorize("hasAuthority('users:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoga um convite pendente", description = "Permissão: users:manage")
    public void revokeInvitation(@PathVariable UUID id) {
        invitations.revoke(id);
    }
}
