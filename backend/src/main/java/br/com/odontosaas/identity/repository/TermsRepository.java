package br.com.odontosaas.identity.repository;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TermsRepository {

    private final JdbcClient jdbc;

    public TermsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasAccepted(UUID userId, String version) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM terms_acceptances WHERE user_id = :user AND version = :version)")
                .param("user", userId)
                .param("version", version)
                .query(Boolean.class)
                .single();
    }

    public int insertIfAbsent(UUID userId, String version, String ip, String userAgent) {
        return jdbc.sql("""
                        INSERT INTO terms_acceptances (user_id, version, ip, user_agent)
                        VALUES (:user, :version, :ip, :userAgent)
                        ON CONFLICT (user_id, version) DO NOTHING
                        """)
                .param("user", userId)
                .param("version", version)
                .param("ip", ip)
                .param("userAgent", userAgent)
                .update();
    }
}
