package br.com.odontosaas.identity.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.config.SecurityProperties;
import br.com.odontosaas.identity.repository.LoginAttemptRepository;
import br.com.odontosaas.shared.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginThrottle {

    private final LoginAttemptRepository attempts;
    private final SecurityProperties props;
    private final AuditService audit;

    public LoginThrottle(LoginAttemptRepository attempts, SecurityProperties props, AuditService audit) {
        this.attempts = attempts;
        this.props = props;
        this.audit = audit;
    }

    public void check(String email, String ip) {
        long windowSeconds = props.loginWindow().toSeconds();
        long emailFailures = attempts.countEmailFailures(email, windowSeconds);
        long ipFailures = ip == null ? 0 : attempts.countIpFailures(ip, windowSeconds);
        if (emailFailures >= props.loginMaxFailuresPerEmail() || ipFailures >= props.loginMaxFailuresPerIp()) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TOO_MANY_ATTEMPTS",
                    "Muitas tentativas",
                    "Muitas tentativas sem sucesso. Aguarde alguns minutos e tente novamente.",
                    null,
                    windowSeconds);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, String ip, UUID userId, AuditAction action) {
        if (action != AuditAction.LOGIN_BLOCKED) {
            insert(email, ip, false);
        }
        audit.recordGlobal(action, userId, "user", userId, Map.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email, String ip) {
        insert(email, ip, true);
    }

    private void insert(String email, String ip, boolean success) {
        attempts.insert(email, ip, success);
    }
}
