package br.com.odontosaas.identity.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados dos tokens de redefinição de senha ({@code password_reset_tokens}). */
@Repository
public class PasswordResetRepository {

    private final JdbcClient jdbc;

    public PasswordResetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Quantidade de tokens criados para o usuário na última hora. */
    public long countCreatedLastHour(UUID userId) {
        return jdbc.sql("""
                        select count(*) from password_reset_tokens
                        where user_id = ? and created_at > now() - interval '1 hour'
                        """).param(userId).query(Long.class).single();
    }

    public void insert(UUID id, UUID userId, String tokenHash, long ttlSeconds) {
        jdbc.sql("""
                        insert into password_reset_tokens (id, user_id, token_hash, expires_at)
                        values (?, ?, ?, now() + make_interval(secs => ?))
                        """).params(id, userId, tokenHash, ttlSeconds).update();
    }

    /** Usuário dono de um token ainda válido (não usado, não expirado), travando a linha ({@code for update}). */
    public Optional<UUID> lockValidUserId(String tokenHash) {
        return jdbc.sql("""
                        select user_id from password_reset_tokens
                        where token_hash = ? and used_at is null and expires_at > now()
                        for update
                        """).param(tokenHash).query(UUID.class).optional();
    }

    /** Marca como usados todos os tokens pendentes do usuário. */
    public void markAllUsed(UUID userId) {
        jdbc.sql("update password_reset_tokens set used_at = now() where user_id = ? and used_at is null")
                .param(userId)
                .update();
    }
}
