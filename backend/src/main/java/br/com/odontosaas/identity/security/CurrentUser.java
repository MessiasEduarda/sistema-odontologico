package br.com.odontosaas.identity.security;

import br.com.odontosaas.shared.ApiException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AuthenticatedUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return find().orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Não autenticado",
                "É necessário estar autenticado para acessar este recurso."));
    }
}
