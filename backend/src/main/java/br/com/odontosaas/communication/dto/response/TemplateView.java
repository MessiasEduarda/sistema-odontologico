package br.com.odontosaas.communication.dto.response;

public record TemplateView(String type, String subject, String body, boolean active, boolean custom) {}
