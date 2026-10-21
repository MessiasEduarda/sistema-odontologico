package br.com.odontosaas.identity.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Remoção periódica de dados temporários de acesso: sessões, tentativas de login e tokens de redefinição. */
@Repository
public class AccessCleanupRepository {

    private final JdbcClient jdbc;

    public AccessCleanupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Apaga sessões expiradas ou revogadas há mais de 30 dias; devolve quantas foram removidas. */
    public int deleteOldSessions() {
        return jdbc.sql("""
                        delete from sessions
                        where expires_at < now() - interval '30 days'
                           or revoked_at < now() - interval '30 days'
                        """).update();
    }

    /** Apaga tentativas de login com mais de 30 dias; devolve quantas foram removidas. */
    public int deleteOldLoginAttempts() {
        return jdbc.sql("delete from login_attempts where attempted_at < now() - interval '30 days'")
                .update();
    }

    /** Apaga tokens de redefinição expirados ou usados há mais de 7 dias; devolve quantos foram removidos. */
    public int deleteOldResetTokens() {
        return jdbc.sql("""
                        delete from password_reset_tokens
                        where expires_at < now() - interval '7 days' or used_at < now() - interval '7 days'
                        """).update();
    }
}
