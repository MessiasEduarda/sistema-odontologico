package br.com.odontosaas.consent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConsentTemplateRequest(
        @NotBlank @Size(max = 160) String title,

        @Pattern(regexp = "DENTAL|AESTHETIC|IMAGE|PRIVACY|GENERAL")
        String category,

        @NotBlank @Size(max = 30000) String body) {}
