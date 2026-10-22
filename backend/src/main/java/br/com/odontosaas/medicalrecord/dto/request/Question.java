package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Question(
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[a-z0-9_]+$")
        String id,

        @NotBlank @Size(max = 60) String section,
        @NotBlank @Size(max = 300) String text,

        @NotBlank @Pattern(regexp = "YES_NO|YES_NO_TEXT|TEXT")
        String type,

        boolean alertOnYes) {}
