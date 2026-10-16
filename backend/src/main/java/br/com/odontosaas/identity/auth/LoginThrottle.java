package br.com.odontosaas.identity.auth;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.identity.security.SecurityProperties;
import br.com.odontosaas.shared.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limita tentativas de autenticação por conta (e-mail) e por IP numa janela deslizante. Registros ficam em
 * {@code login_attempts}, funcionando com várias instâncias da API.
 */
@Component
public class LoginThrottle {

    private final JdbcClient jdbc;
    private final SecurityProperties props;
    private final AuditService audit;

    public LoginThrottle(JdbcClient jdbc, SecurityProperties props, AuditService audit) {
        this.jdbc = jdbc;
        this.props = props;
        this.audit = audit;
    }

    /** Lança 429 se o e-mail ou o IP excederam o limite de falhas na janela. */
    public void check(String email, String ip) {
        long windowSeconds = props.loginWindow().toSeconds();
        long emailFailures =
                jdbc.sql("""
                        select count(*) from login_attempts
                        where email = ? and not success and attempted_at > now() - make_interval(secs => ?)
                        """).params(email, windowSeconds).query(Long.class).single();
        long ipFailures = ip == null
                ? 0
                : jdbc.sql("""
                                select count(*) from login_attempts
                                where ip = ? and not success and attempted_at > now() - make_interval(secs => ?)
                                """).params(ip, windowSeconds).query(Long.class).single();
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

    /**
     * Registra falha + auditoria em transação PRÓPRIA: precisam ficar gravadas mesmo que a requisição termine em erro.
     * Tentativas bloqueadas não contam como nova falha (não prolongam o bloqueio indefinidamente).
     */
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
        jdbc.sql("insert into login_attempts (email, ip, success) values (?, ?, ?)")
                .params(email, ip, success)
                .update();
    }
}
