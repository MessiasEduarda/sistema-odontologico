package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FormRequest(
        @NotBlank @Size(max = 120) String name,

        @NotBlank @Pattern(regexp = "DENTAL|AESTHETIC|OTHER")
        String kind,

        @NotEmpty @Size(max = 80) List<@Valid Question> questions) {}
