package br.com.odontosaas.identity.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID sessionId,
        String email,
        String name,
        UUID tenantId,
        Set<Permission> permissions,
        boolean platformAdmin) {

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
