package br.com.odontosaas.identity.dto.response;

import java.util.UUID;

public record ClinicSummary(UUID id, String name, String slug) {}
