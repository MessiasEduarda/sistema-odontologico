package br.com.odontosaas.identity.dto.response;

import java.util.Set;
import java.util.UUID;

public record RoleView(UUID id, String code, String name, boolean systemRole, Set<String> permissions) {}
