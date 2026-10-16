package br.com.odontosaas.identity.auth;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.AuthenticatedUser;
import br.com.odontosaas.identity.mail.IdentityMailer;
import br.com.odontosaas.identity.security.SecurityProperties;
import br.com.odontosaas.identity.session.SessionStore;
import br.com.odontosaas.identity.user.User;
import br.com.odontosaas.identity.user.UserRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.SecureTokens;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final JdbcClient jdbc;
    private final SessionStore sessions;
    private final IdentityMailer mailer;
    private final AuditService audit;
    private final SecurityProperties props;

    public PasswordService(
            UserRepository users,
            PasswordEncoder encoder,
            PasswordPolicy policy,
            JdbcClient jdbc,
            SessionStore sessions,
            IdentityMailer mailer,
            AuditService audit,
            SecurityProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.mailer = mailer;
        this.audit = audit;
        this.props = props;
    }

    /**
     * Sempre "funciona" do ponto de vista do cliente (202), exista ou não a conta — não revela quais e-mails estão
     * cadastrados. Limita a quantidade de e-mails por conta por hora.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(rawEmail));
        if (found.isEmpty() || !found.get().isActive()) return;
        User user = found.get();

        long recent = jdbc.sql("""
                        select count(*) from password_reset_tokens
                        where user_id = ? and created_at > now() - interval '1 hour'
                        """).param(user.getId()).query(Long.class).single();
        if (recent >= props.passwordResetMaxPerHour()) return;

        String raw = SecureTokens.generate();
        jdbc.sql("""
                        insert into password_reset_tokens (id, user_id, token_hash, expires_at)
                        values (?, ?, ?, now() + make_interval(secs => ?))
                        """)
                .params(
                        Ids.newId(),
                        user.getId(),
                        SecureTokens.sha256Hex(raw),
                        props.passwordResetTtl().toSeconds())
                .update();
        audit.recordGlobal(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), "user", user.getId(), Map.of());
        mailer.sendPasswordReset(user.getEmail(), user.getName(), raw);
    }

    /** Define nova senha com token de uso único e derruba TODAS as sessões do usuário. */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        ApiException invalid = ApiException.badRequest(
                "INVALID_TOKEN", "Este link de redefinição é inválido ou expirou. Solicite um novo.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;

        UUID userId = jdbc.sql("""
                        select user_id from password_reset_tokens
                        where token_hash = ? and used_at is null and expires_at > now()
                        for update
                        """)
                .param(SecureTokens.sha256Hex(rawToken))
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> invalid);
        User user = users.findById(userId).filter(User::isActive).orElseThrow(() -> invalid);

        policy.validate("newPassword", newPassword, user.getEmail());
        user.changePassword(encoder.encode(newPassword));
        users.saveAndFlush(user);

        jdbc.sql("update password_reset_tokens set used_at = now() where user_id = ? and used_at is null")
                .param(userId)
                .update();
        sessions.revokeAllForUser(userId, "PASSWORD_RESET", null);
        audit.recordGlobal(AuditAction.PASSWORD_RESET_COMPLETED, userId, "user", userId, Map.of());
    }

    /** Troca de senha autenticada: exige a senha atual e encerra as OUTRAS sessões. */
    @Transactional
    public void change(AuthenticatedUser current, String currentPassword, String newPassword) {
        User user = users.findById(current.userId()).orElseThrow(() -> ApiException.notFound("Usuário"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Dados inválidos",
                    "Um ou mais campos não passaram na validação.",
                    java.util.List.of(Map.of("field", "currentPassword", "message", "Senha atual incorreta.")),
                    null);
        }
        policy.validate("newPassword", newPassword, user.getEmail());
        user.changePassword(encoder.encode(newPassword));
        users.saveAndFlush(user);
        sessions.revokeAllForUser(user.getId(), "PASSWORD_CHANGED", current.sessionId());
        audit.recordGlobal(AuditAction.PASSWORD_CHANGED, user.getId(), "user", user.getId(), Map.of());
    }
}
