package br.com.odontosaas.identity.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.dto.response.SessionView;
import br.com.odontosaas.identity.repository.SessionStore;
import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.shared.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private static final String REASON = "USER_REVOKED";

    private final SessionStore sessions;
    private final AuditService audit;

    public SessionService(SessionStore sessions, AuditService audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    public List<SessionView> list(AuthenticatedUser user) {
        return sessions.listActive(user.userId()).stream()
                .map(s -> new SessionView(
                        s.id(), s.createdAt(), s.lastSeenAt(), s.ip(), s.userAgent(), s.id().equals(user.sessionId())))
                .toList();
    }

    public void revoke(AuthenticatedUser user, UUID sessionId) {
        if (!sessions.revokeOwned(user.userId(), sessionId, REASON)) {
            throw ApiException.notFound("Sessão");
        }
        audit.recordGlobal(AuditAction.SESSION_REVOKED, user.userId(), "session", sessionId, Map.of());
    }

    public void revokeOthers(AuthenticatedUser user) {
        int count = sessions.revokeAllForUser(user.userId(), REASON, user.sessionId());
        audit.recordGlobal(AuditAction.SESSION_REVOKED, user.userId(), "session", null, Map.of("count", count));
    }
}
