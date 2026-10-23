package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 20000) String content) {}
