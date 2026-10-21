package br.com.odontosaas.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptTermsRequest(@NotBlank @Size(max = 20) String version) {}
