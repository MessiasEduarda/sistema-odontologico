package br.com.odontosaas.identity.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.config.SecurityProperties;
import br.com.odontosaas.identity.entity.User;
import br.com.odontosaas.identity.repository.PasswordResetRepository;
import br.com.odontosaas.identity.repository.SessionStore;
import br.com.odontosaas.identity.repository.UserRepository;
import br.com.odontosaas.identity.security.AuthenticatedUser;
import br.com.odontosaas.identity.support.PasswordPolicy;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.SecureTokens;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final PasswordResetRepository resetTokens;
    private final SessionStore sessions;
    private final IdentityMailer mailer;
    private final AuditService audit;
    private final SecurityProperties props;

    public PasswordService(
            UserRepository users,
            PasswordEncoder encoder,
            PasswordPolicy policy,
            PasswordResetRepository resetTokens,
            SessionStore sessions,
            IdentityMailer mailer,
            AuditService audit,
            SecurityProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.policy = policy;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.mailer = mailer;
        this.audit = audit;
        this.props = props;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(rawEmail));
        if (found.isEmpty() || !found.get().isActive()) return;
        User user = found.get();

        long recent = resetTokens.countCreatedLastHour(user.getId());
        if (recent >= props.passwordResetMaxPerHour()) return;

        String raw = SecureTokens.generate();
        resetTokens.insert(
                Ids.newId(),
                user.getId(),
                SecureTokens.sha256Hex(raw),
                props.passwordResetTtl().toSeconds());
        audit.recordGlobal(AuditAction.PASSWORD_RESET_REQUESTED, user.getId(), "user", user.getId(), Map.of());
        mailer.sendPasswordReset(user.getEmail(), user.getName(), raw);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        ApiException invalid = ApiException.badRequest(
                "INVALID_TOKEN", "Este link de redefinição é inválido ou expirou. Solicite um novo.");
        if (!SecureTokens.looksValid(rawToken)) throw invalid;

        UUID userId =
                resetTokens.lockValidUserId(SecureTokens.sha256Hex(rawToken)).orElseThrow(() -> invalid);
        User user = users.findById(userId).filter(User::isActive).orElseThrow(() -> invalid);

        policy.validate("newPassword", newPassword, user.getEmail());
        user.changePassword(encoder.encode(newPassword));
        users.saveAndFlush(user);

        resetTokens.markAllUsed(userId);
        sessions.revokeAllForUser(userId, "PASSWORD_RESET", null);
        audit.recordGlobal(AuditAction.PASSWORD_RESET_COMPLETED, userId, "user", userId, Map.of());
    }

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
