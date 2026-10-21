package br.com.odontosaas.patients.dto.response;

/** Resultado: {@code ready} linhas válidas; {@code created} só é maior que zero quando não é simulação. */
public record ImportResult(int total, int ready, int created, java.util.List<ImportIssue> issues) {}
