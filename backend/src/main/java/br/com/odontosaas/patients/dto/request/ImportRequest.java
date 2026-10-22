package br.com.odontosaas.patients.dto.request;

public record ImportRequest(
        @jakarta.validation.constraints.NotNull java.util.List<ImportRow> rows, boolean dryRun) {}
