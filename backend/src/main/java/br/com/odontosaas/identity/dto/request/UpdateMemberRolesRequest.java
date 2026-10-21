package br.com.odontosaas.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateMemberRolesRequest(
        @NotEmpty @Size(max = 10) List<@NotBlank String> roleCodes) {}
