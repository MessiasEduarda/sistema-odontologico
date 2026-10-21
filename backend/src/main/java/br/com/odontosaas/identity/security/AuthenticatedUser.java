package br.com.odontosaas.identity.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal da requisição autenticada, montado pelo servidor a partir da sessão. {@code tenantId} é a clínica ativa
 * (nula se o usuário ainda não selecionou uma) e {@code permissions} são as permissões NESSA clínica.
 */
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
