package br.com.odontosaas.identity.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccessCleanupRepository {

    private final JdbcClient jdbc;

    public AccessCleanupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int deleteOldSessions() {
        return jdbc.sql("""
                        delete from sessions
                        where expires_at < now() - interval '30 days'
                           or revoked_at < now() - interval '30 days'
                        """).update();
    }

    public int deleteOldLoginAttempts() {
        return jdbc.sql("delete from login_attempts where attempted_at < now() - interval '30 days'")
                .update();
    }

    public int deleteOldResetTokens() {
        return jdbc.sql("""
                        delete from password_reset_tokens
                        where expires_at < now() - interval '7 days' or used_at < now() - interval '7 days'
                        """).update();
    }
}
