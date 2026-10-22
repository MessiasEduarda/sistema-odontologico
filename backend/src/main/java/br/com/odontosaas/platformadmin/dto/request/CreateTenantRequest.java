package br.com.odontosaas.platformadmin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 160) String name,

        @NotBlank
        @Size(min = 3, max = 60)
        @Pattern(regexp = "^[a-z0-9](-?[a-z0-9])*$", message = "use apenas letras minúsculas, números e hífens")
        String slug,

        @NotBlank @Email @Size(max = 254) String ownerEmail,
        @Pattern(regexp = "^[A-Z0-9_]{2,40}$") String planCode) {}
