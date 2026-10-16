package br.com.odontosaas.identity.auth;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.RequestInfo;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aceite dos Termos de uso e da Política de privacidade. Cada pessoa aceita uma única vez por versão; ao mudar o
 * texto, aumente {@link #CURRENT_VERSION} (e a data em LegalPage no front-end) para pedir um novo aceite a todos.
 */
@Service
public class TermsService {

    /** Versão vigente: data da última atualização dos textos. */
    public static final String CURRENT_VERSION = "2026-10-15";

    private final JdbcClient jdbc;
    private final AuditService audit;

    public TermsService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public boolean hasAcceptedCurrent(UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM terms_acceptances WHERE user_id = :user AND version = :version)")
                .param("user", userId)
                .param("version", CURRENT_VERSION)
                .query(Boolean.class)
                .single();
    }

    /** Registra o aceite (idempotente). Recusa versão diferente da vigente: a tela estava com um texto antigo. */
    @Transactional
    public void accept(UUID userId, String version) {
        if (!CURRENT_VERSION.equals(version)) {
            throw ApiException.conflict(
                    "TERMS_OUTDATED", "Os termos foram atualizados. Recarregue a página para ler a versão vigente.");
        }
        int inserted = jdbc.sql("""
                        INSERT INTO terms_acceptances (user_id, version, ip, user_agent)
                        VALUES (:user, :version, :ip, :userAgent)
                        ON CONFLICT (user_id, version) DO NOTHING
                        """)
                .param("user", userId)
                .param("version", version)
                .param("ip", RequestInfo.clientIp())
                .param("userAgent", RequestInfo.userAgent())
                .update();
        if (inserted > 0) {
            audit.recordGlobal(AuditAction.TERMS_ACCEPTED, userId, "terms", null, Map.of("version", version));
        }
    }
}
