package br.com.odontosaas.patients.dto.response;

public record ImportResult(int total, int ready, int created, java.util.List<ImportIssue> issues) {}
