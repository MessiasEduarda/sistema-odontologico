package br.com.odontosaas.medicalrecord.dto.request;

public record ActiveRequest(
        @jakarta.validation.constraints.NotNull Boolean active) {}
