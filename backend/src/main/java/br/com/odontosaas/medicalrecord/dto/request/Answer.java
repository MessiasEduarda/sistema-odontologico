package br.com.odontosaas.medicalrecord.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Answer(
        @Pattern(regexp = "YES|NO") String answer,
        @Size(max = 1000) String detail) {}
