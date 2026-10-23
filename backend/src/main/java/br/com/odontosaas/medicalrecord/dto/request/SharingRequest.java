package br.com.odontosaas.medicalrecord.dto.request;

public record SharingRequest(
        @jakarta.validation.constraints.NotNull Boolean shared) {}
