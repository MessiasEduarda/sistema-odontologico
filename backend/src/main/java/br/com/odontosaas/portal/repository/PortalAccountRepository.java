package br.com.odontosaas.portal.repository;

import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.RequestInfo;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PortalAccountRepository {

    public record AccountRow(
            UUID id, String email, String passwordHash, String status, Instant activatedAt, Instant lastLoginAt) {}

    public record TokenRow(UUID id, UUID accountId, UUID tenantId, UUID patientId, int attempts) {}

    public record SessionRow(UUID id, UUID accountId, UUID tenantId, UUID patientId) {}

    public record ChallengeRow(UUID id, UUID accountId, String codeHash, int attempts) {}

    public record LinkRow(UUID tenantId, UUID patientId, UUID accountId, Instant invitedAt, Instant blockedAt) {}

    public record PatientInfo(String fullName, String email, LocalDate birthDate) {}

    private final JdbcClient jdbc;

    public PortalAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountRow> findByEmail(String email) {
        return jdbc.sql("""
                        select id, email, password_hash, status, activated_at, last_login_at from portal_accounts
                        where lower(email) = lower(?)
                        """).param(email.strip()).query(AccountRow.class).optional();
    }

    public Optional<AccountRow> find(UUID id) {
        return jdbc.sql("""
                        select id, email, password_hash, status, activated_at, last_login_at from portal_accounts
                        where id = ?
                        """).param(id).query(AccountRow.class).optional();
    }

    public UUID insertAccount(String email) {
        UUID id = Ids.newId();
        jdbc.sql("insert into portal_accounts (id, email) values (?, ?)")
                .params(id, email.strip().toLowerCase())
                .update();
        return id;
    }

    public void activate(UUID accountId, String passwordHash) {
        jdbc.sql("""
                        update portal_accounts set password_hash = ?, status = 'ACTIVE', activated_at = now(),
                            password_changed_at = now(), terms_accepted_at = now()
                        where id = ?
                        """).params(passwordHash, accountId).update();
    }

    public void changePassword(UUID accountId, String passwordHash) {
        jdbc.sql("update portal_accounts set password_hash = ?, password_changed_at = now() where id = ?")
                .params(passwordHash, accountId)
                .update();
    }

    public void touchLogin(UUID accountId) {
        jdbc.sql("update portal_accounts set last_login_at = now() where id = ?")
                .param(accountId)
                .update();
    }

    public void insertToken(
            UUID accountId, String purpose, String tokenHash, UUID tenantId, UUID patientId, Instant expiresAt) {
        jdbc.sql("""
                        insert into portal_account_tokens (id, account_id, purpose, token_hash, invited_tenant_id, invited_patient_id,
                            expires_at) values (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(Ids.newId(), accountId, purpose, tokenHash, tenantId, patientId, Timestamp.from(expiresAt))
                .update();
    }

    public Optional<TokenRow> findValidToken(String tokenHash, String purpose) {
        return jdbc.sql("""
                        select id, account_id, invited_tenant_id as tenant_id, invited_patient_id as patient_id, attempts
                        from portal_account_tokens
                        where token_hash = ? and purpose = ? and used_at is null and expires_at > now()
                        """).params(tokenHash, purpose).query(TokenRow.class).optional();
    }

    public int registerTokenFailure(UUID tokenId) {
        return jdbc.sql("update portal_account_tokens set attempts = attempts + 1 where id = ? returning attempts")
                .param(tokenId)
                .query(Integer.class)
                .single();
    }

    public void useToken(UUID tokenId) {
        jdbc.sql("update portal_account_tokens set used_at = now() where id = ? and used_at is null")
                .param(tokenId)
                .update();
    }

    public void invalidateTokens(UUID accountId, String purpose) {
        jdbc.sql("""
                        update portal_account_tokens set used_at = now()
                        where account_id = ? and purpose = ? and used_at is null
                        """).params(accountId, purpose).update();
    }

    public int tokensCreatedSince(UUID accountId, String purpose, long seconds) {
        return jdbc.sql("""
                        select count(*) from portal_account_tokens
                        where account_id = ? and purpose = ? and created_at > now() - make_interval(secs => ?)
                        """)
                .params(accountId, purpose, seconds)
                .query(Integer.class)
                .single();
    }

    public UUID createSession(String tokenHash, UUID accountId, UUID tenantId, UUID patientId, long absoluteSeconds) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into portal_sessions (id, token_hash, account_id, active_tenant_id, active_patient_id, expires_at, ip,
                            user_agent)
                        values (?, ?, ?, ?, ?, now() + make_interval(secs => ?), ?, ?)
                        """)
                .params(
                        id,
                        tokenHash,
                        accountId,
                        tenantId,
                        patientId,
                        absoluteSeconds,
                        RequestInfo.clientIp(),
                        RequestInfo.userAgent())
                .update();
        return id;
    }

    public Optional<SessionRow> findActiveSession(String tokenHash, long idleSeconds) {
        return jdbc.sql("""
                        select s.id, s.account_id, s.active_tenant_id as tenant_id, s.active_patient_id as patient_id
                        from portal_sessions s join portal_accounts a on a.id = s.account_id
                        where s.token_hash = ? and s.revoked_at is null and s.expires_at > now()
                          and s.last_seen_at > now() - make_interval(secs => ?) and a.status = 'ACTIVE'
                        """)
                .params(tokenHash, idleSeconds)
                .query(SessionRow.class)
                .optional();
    }

    public void touchSession(UUID sessionId) {
        jdbc.sql("""
                        update portal_sessions set last_seen_at = now()
                        where id = ? and last_seen_at < now() - interval '1 minute'
                        """).param(sessionId).update();
    }

    public void selectProfile(UUID sessionId, UUID tenantId, UUID patientId) {
        jdbc.sql("update portal_sessions set active_tenant_id = ?, active_patient_id = ? where id = ?")
                .params(tenantId, patientId, sessionId)
                .update();
    }

    public void revokeSession(UUID sessionId) {
        jdbc.sql("update portal_sessions set revoked_at = now() where id = ? and revoked_at is null")
                .param(sessionId)
                .update();
    }

    public void revokeAllSessions(UUID accountId) {
        jdbc.sql("update portal_sessions set revoked_at = now() where account_id = ? and revoked_at is null")
                .param(accountId)
                .update();
    }

    public void revokeProfileSessions(UUID tenantId, UUID patientId) {
        jdbc.sql("""
                        update portal_sessions set revoked_at = now()
                        where active_tenant_id = ? and active_patient_id = ? and revoked_at is null
                        """).params(tenantId, patientId).update();
    }

    public void trustDevice(UUID accountId, String tokenHash, Instant expiresAt) {
        jdbc.sql("""
                        insert into portal_trusted_devices (id, account_id, token_hash, expires_at, user_agent)
                        values (?, ?, ?, ?, ?)
                        """)
                .params(Ids.newId(), accountId, tokenHash, Timestamp.from(expiresAt), RequestInfo.userAgent())
                .update();
    }

    public boolean isTrustedDevice(UUID accountId, String tokenHash) {
        return jdbc.sql("""
                                update portal_trusted_devices set last_used_at = now()
                                where account_id = ? and token_hash = ? and revoked_at is null and expires_at > now()
                                """).params(accountId, tokenHash).update() > 0;
    }

    public void revokeDevices(UUID accountId) {
        jdbc.sql("update portal_trusted_devices set revoked_at = now() where account_id = ? and revoked_at is null")
                .param(accountId)
                .update();
    }

    public UUID createChallenge(UUID accountId, String codeHash, Instant expiresAt) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into portal_login_challenges (id, account_id, code_hash, expires_at)
                        values (?, ?, ?, ?)
                        """).params(id, accountId, codeHash, Timestamp.from(expiresAt)).update();
        return id;
    }

    public Optional<ChallengeRow> findValidChallenge(UUID id) {
        return jdbc.sql("""
                        select id, account_id, code_hash, attempts from portal_login_challenges
                        where id = ? and used_at is null and expires_at > now()
                        """).param(id).query(ChallengeRow.class).optional();
    }

    public int registerChallengeFailure(UUID id) {
        return jdbc.sql("update portal_login_challenges set attempts = attempts + 1 where id = ? returning attempts")
                .param(id)
                .query(Integer.class)
                .single();
    }

    public void useChallenge(UUID id) {
        jdbc.sql("update portal_login_challenges set used_at = now() where id = ?")
                .param(id)
                .update();
    }

    public void recordAttempt(String email, String ip, boolean success) {
        jdbc.sql("insert into portal_login_attempts (id, email, ip, success) values (?, ?, ?, ?)")
                .params(Ids.newId(), email.strip().toLowerCase(), ip, success)
                .update();
    }

    public long countEmailFailures(String email, long seconds) {
        return jdbc.sql("""
                        select count(*) from portal_login_attempts
                        where lower(email) = lower(?) and not success and created_at > now() - make_interval(secs => ?)
                        """).params(email.strip(), seconds).query(Long.class).single();
    }

    public long countIpFailures(String ip, long seconds) {
        return jdbc.sql("""
                        select count(*) from portal_login_attempts
                        where ip = ? and not success and created_at > now() - make_interval(secs => ?)
                        """).params(ip, seconds).query(Long.class).single();
    }

    public Optional<LinkRow> findLink(UUID tenantId, UUID patientId) {
        return jdbc.sql("""
                        select tenant_id, patient_id, account_id, invited_at, blocked_at from portal_account_links
                        where tenant_id = ? and patient_id = ?
                        """).params(tenantId, patientId).query(LinkRow.class).optional();
    }

    public void saveLink(UUID tenantId, UUID patientId, UUID accountId) {
        jdbc.sql("""
                        insert into portal_account_links (tenant_id, patient_id, account_id) values (?, ?, ?)
                        on conflict (tenant_id, patient_id)
                        do update set account_id = excluded.account_id, invited_at = now(), blocked_at = null
                        """).params(tenantId, patientId, accountId).update();
    }

    public void deleteLink(UUID tenantId, UUID patientId) {
        jdbc.sql("delete from portal_account_links where tenant_id = ? and patient_id = ?")
                .params(tenantId, patientId)
                .update();
    }

    public int setBlocked(UUID tenantId, UUID patientId, boolean blocked) {
        return jdbc.sql("""
                        update portal_account_links set blocked_at = case when ? then now() else null end
                        where tenant_id = ? and patient_id = ?
                        """).params(blocked, tenantId, patientId).update();
    }

    public List<LinkRow> linksOf(UUID accountId) {
        return jdbc.sql("""
                        select tenant_id, patient_id, account_id, invited_at, blocked_at from portal_account_links
                        where account_id = ? order by invited_at
                        """).param(accountId).query(LinkRow.class).list();
    }

    public Optional<PatientInfo> patient(UUID tenantId, UUID patientId) {
        return jdbc.sql("select full_name, email, birth_date from patients where tenant_id = ? and id = ?")
                .params(tenantId, patientId)
                .query(PatientInfo.class)
                .optional();
    }

    public String clinicName(UUID tenantId) {
        return jdbc.sql("select name from tenants where id = ?")
                .param(tenantId)
                .query(String.class)
                .single();
    }
}
