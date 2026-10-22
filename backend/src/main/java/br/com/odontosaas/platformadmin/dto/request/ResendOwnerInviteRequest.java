package br.com.odontosaas.platformadmin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendOwnerInviteRequest(
        @NotBlank @Email @Size(max = 254) String ownerEmail) {}
