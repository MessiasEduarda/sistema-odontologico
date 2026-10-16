package br.com.odontosaas.identity.session;

import br.com.odontosaas.identity.security.SecurityProperties;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.shared.SecureTokens;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sessões opacas no servidor (ADR-0005). O token bruto vai apenas no cookie; o banco guarda o SHA-256. Uma sessão é
 * válida se não foi revogada, não passou da expiração absoluta, foi usada dentro do limite de inatividade e o usuário
 * está ativo.
 */
@Component
public class SessionStore {

    public record NewSession(UUID id, String rawToken) {}

    public record ActiveSession(
            UUID id,
            UUID userId,
            UUID activeTenantId,
            Instant lastSeenAt,
            String email,
            String name,
            String platformRole) {}

    public record SessionInfo(UUID id, Instant createdAt, Instant lastSeenAt, String ip, String userAgent) {}

    private final JdbcClient jdbc;
    private final SecurityProperties props;

    public SessionStore(JdbcClient jdbc, SecurityProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public NewSession create(UUID userId, UUID activeTenantId) {
        String raw = SecureTokens.generate();
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into sessions (id, token_hash, user_id, active_tenant_id, expires_at, ip, user_agent)
                        values (?, ?, ?, ?, now() + make_interval(secs => ?), ?, ?)
                        """)
                .params(
                        id,
                        SecureTokens.sha256Hex(raw),
                        userId,
                        activeTenantId,
                        props.sessionAbsoluteTimeout().toSeconds(),
                        RequestInfo.clientIp(),
                        RequestInfo.userAgent())
                .update();
        return new NewSession(id, raw);
    }

    public Optional<ActiveSession> findActive(String rawToken) {
        if (!SecureTokens.looksValid(rawToken)) return Optional.empty();
        return jdbc.sql("""
                        select s.id, s.user_id, s.active_tenant_id, s.last_seen_at, u.email, u.name, u.platform_role
                        from sessions s join users u on u.id = s.user_id
                        where s.token_hash = ?
                          and s.revoked_at is null
                          and s.expires_at > now()
                          and s.last_seen_at > now() - make_interval(secs => ?)
                          and u.status = 'ACTIVE'
                        """)
                .params(
                        SecureTokens.sha256Hex(rawToken),
                        props.sessionIdleTimeout().toSeconds())
                .query((rs, n) -> new ActiveSession(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("active_tenant_id", UUID.class),
                        rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getString("email"),
                        rs.getString("name"),
                        rs.getString("platform_role")))
                .optional();
    }

    /** Atualiza o "último uso" no máximo uma vez por minuto (evita escrita a cada requisição). */
    public void touch(UUID sessionId) {
        jdbc.sql("update sessions set last_seen_at = now() where id = ? and last_seen_at < now() - interval '1 minute'")
                .param(sessionId)
                .update();
    }

    public void revoke(UUID sessionId, String reason) {
        jdbc.sql("update sessions set revoked_at = now(), revoke_reason = ? where id = ? and revoked_at is null")
                .params(reason, sessionId)
                .update();
    }

    /** Revoga uma sessão do próprio usuário. Retorna falso se ela não existir ou for de outra pessoa. */
    public boolean revokeOwned(UUID userId, UUID sessionId, String reason) {
        return jdbc.sql("""
                                update sessions set revoked_at = now(), revoke_reason = ?
                                where id = ? and user_id = ? and revoked_at is null
                                """).params(reason, sessionId, userId).update() > 0;
    }

    public int revokeAllForUser(UUID userId, String reason, UUID exceptSessionId) {
        return jdbc.sql("""
                        update sessions set revoked_at = now(), revoke_reason = ?
                        where user_id = ? and revoked_at is null and (cast(? as uuid) is null or id <> ?)
                        """)
                .params(reason, userId, exceptSessionId, exceptSessionId)
                .update();
    }

    /** Usado ao desativar um vínculo: derruba as sessões do usuário que estão com essa clínica ativa. */
    public int revokeForUserInTenant(UUID userId, UUID tenantId, String reason) {
        return jdbc.sql("""
                        update sessions set revoked_at = now(), revoke_reason = ?
                        where user_id = ? and active_tenant_id = ? and revoked_at is null
                        """).params(reason, userId, tenantId).update();
    }

    public List<SessionInfo> listActive(UUID userId) {
        return jdbc.sql("""
                        select id, created_at, last_seen_at, ip, user_agent from sessions
                        where user_id = ? and revoked_at is null and expires_at > now()
                          and last_seen_at > now() - make_interval(secs => ?)
                        order by last_seen_at desc
                        """)
                .params(userId, props.sessionIdleTimeout().toSeconds())
                .query((rs, n) -> new SessionInfo(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getString("ip"),
                        rs.getString("user_agent")))
                .list();
    }
}
