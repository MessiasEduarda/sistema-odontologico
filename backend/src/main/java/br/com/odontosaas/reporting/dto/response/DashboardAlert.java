package br.com.odontosaas.reporting.dto.response;

public record DashboardAlert(String type, String severity, String message, long count) {}
