package br.com.odontosaas.identity.access;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contratos (DTOs) de equipe, papéis e convites da clínica. */
public final class AccessDtos {

    private AccessDtos() {}

    public record CreateInvitationRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 40) String roleCode) {}

    public record InvitationView(
            UUID id, String email, String roleCode, String status, Instant expiresAt, Instant createdAt) {}

    public record TokenRequest(@NotBlank @Size(max = 64) String token) {}

    public record InvitationPreview(String clinicName, String email, String roleName, boolean existingAccount) {}

    /**
     * Aceite de convite. Conta nova: {@code name} + {@code password} (nova senha). Conta existente: {@code password}
     * é a senha atual dela (confirma que quem aceita é o dono do e-mail).
     */
    public record AcceptInvitationRequest(
            @NotBlank @Size(max = 64) String token,
            @Size(max = 160) String name,
            @NotBlank String password) {}

    public record MemberView(
            UUID membershipId, UUID userId, String name, String email, String status, Set<String> roles) {}

    public record UpdateMemberRolesRequest(
            @NotEmpty @Size(max = 10) List<@NotBlank String> roleCodes) {}

    public record RoleView(UUID id, String code, String name, boolean systemRole, Set<String> permissions) {}
}
