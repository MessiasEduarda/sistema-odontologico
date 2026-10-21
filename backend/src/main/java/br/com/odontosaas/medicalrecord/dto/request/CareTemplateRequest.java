package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CareTemplateRequest(
        @NotBlank @Size(max = 160) String title,

        @NotBlank @Pattern(regexp = "DENTAL|AESTHETIC|GENERAL")
        String category,

        @NotBlank @Pattern(regexp = "BEFORE|AFTER|GENERAL") String moment,

        @NotBlank @Size(max = 20000) String body,
        Boolean active) {}
