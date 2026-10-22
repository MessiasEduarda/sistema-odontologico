package br.com.odontosaas.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank @Size(max = 64) String token,
        @Size(max = 160) String name,
        @NotBlank String password) {}
