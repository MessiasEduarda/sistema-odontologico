package br.com.odontosaas.identity.security;

import br.com.odontosaas.identity.repository.AccessQueries;
import br.com.odontosaas.identity.repository.SessionStore;
import br.com.odontosaas.identity.repository.SessionStore.ActiveSession;
import br.com.odontosaas.identity.support.SessionCookies;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionStore sessions;
    private final SessionCookies cookies;
    private final AccessQueries access;

    public SessionAuthenticationFilter(SessionStore sessions, SessionCookies cookies, AccessQueries access) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.access = access;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Optional<String> token = cookies.read(request);
            if (token.isPresent()) {
                Optional<ActiveSession> session = sessions.findActive(token.get());
                if (session.isPresent()) {
                    authenticate(session.get());
                } else {
                    cookies.clear(response);
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(ActiveSession session) {
        UUID tenantId = session.activeTenantId();
        Set<Permission> permissions = Set.of();
        if (tenantId != null) {
            TenantContext.set(tenantId, session.userId());
            Optional<Set<Permission>> inTenant = access.permissionsIn(tenantId, session.userId());
            if (inTenant.isPresent()) {
                permissions = inTenant.get();
            } else {
                tenantId = null; // vínculo desativado ou clínica suspensa: segue autenticado, sem clínica ativa
            }
        }
        boolean platformAdmin = "PLATFORM_ADMIN".equals(session.platformRole());
        TenantContext.set(tenantId, session.userId(), platformAdmin);
        sessions.touch(session.id());
        AuthenticatedUser principal = new AuthenticatedUser(
                session.userId(), session.id(), session.email(), session.name(), tenantId, permissions, platformAdmin);

        List<GrantedAuthority> authorities = new ArrayList<>();
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.code())));
        if (platformAdmin) authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        SecurityContextHolder.setContext(context);
    }
}
