package br.com.odontosaas.identity.dto.response;

import java.util.UUID;

public record UserView(UUID id, String name, String email) {}
