package br.com.odontosaas.identity.access;

import br.com.odontosaas.identity.user.User;
import br.com.odontosaas.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Convite para um e-mail entrar em uma clínica com um papel. O banco guarda só o hash do token. */
@Entity
@Table(name = "invitations")
public class Invitation extends BaseEntity {

    public enum Status {
        PENDING,
        ACCEPTED,
        REVOKED
    }

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "invited_by", updatable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Invitation() {}

    public Invitation(UUID tenantId, String email, UUID roleId, String tokenHash, UUID invitedBy, Instant expiresAt) {
        this.tenantId = tenantId;
        this.email = User.normalizeEmail(email);
        this.roleId = roleId;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return status == Status.PENDING && expiresAt.isAfter(now);
    }

    public void accept(Instant now) {
        this.status = Status.ACCEPTED;
        this.acceptedAt = now;
    }

    public void revoke() {
        this.status = Status.REVOKED;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
