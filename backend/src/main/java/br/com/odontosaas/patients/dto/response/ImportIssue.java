package br.com.odontosaas.patients.dto.response;

/** {@code kind}: ERROR (linha inválida) ou DUPLICATE (CPF já cadastrado ou repetido na planilha). */
public record ImportIssue(int line, String name, String kind, String message) {}
