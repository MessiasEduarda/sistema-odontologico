package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AttachmentUpdateRequest(
        @NotBlank @Pattern(regexp = "XRAY|PHOTO|EXAM|DOCUMENT|OTHER")
        String category,

        @Size(max = 300) String description) {}
