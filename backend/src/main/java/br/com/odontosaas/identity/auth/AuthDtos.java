package br.com.odontosaas.identity.auth;

import br.com.odontosaas.identity.access.AccessQueries.ClinicSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos (DTOs) dos endpoints de autenticação. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {}

    public record SwitchClinicRequest(@NotNull UUID clinicId) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 254) String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 64) String token,
            @NotBlank String newPassword) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank String newPassword) {}

    public record UserView(UUID id, String name, String email) {}

    /** Quem sou eu: usuário, clínica ativa, permissões nela e clínicas disponíveis para troca. */
    public record MeResponse(
            UserView user,
            boolean platformAdmin,
            ClinicSummary activeClinic,
            List<String> permissions,
            List<ClinicSummary> clinics) {}

    public record SessionView(
            UUID id, Instant createdAt, Instant lastSeenAt, String ip, String userAgent, boolean current) {}
}
