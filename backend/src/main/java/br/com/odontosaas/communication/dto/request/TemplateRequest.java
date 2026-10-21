package br.com.odontosaas.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TemplateRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 5000) String body,
        Boolean active) {}
