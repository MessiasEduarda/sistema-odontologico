package br.com.odontosaas.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Aceite de convite. Conta nova: {@code name} + {@code password} (nova senha). Conta existente: {@code password}
 * é a senha atual dela (confirma que quem aceita é o dono do e-mail).
 */
public record AcceptInvitationRequest(
        @NotBlank @Size(max = 64) String token,
        @Size(max = 160) String name,
        @NotBlank String password) {}
