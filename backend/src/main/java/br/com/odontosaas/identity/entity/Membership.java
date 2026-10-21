package br.com.odontosaas.identity.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

/** Vínculo de um usuário com uma clínica (tenant). Os papéis ficam em {@code membership_roles}. */
@Entity
@Table(name = "memberships")
public class Membership extends BaseEntity {

    public enum Status {
        ACTIVE,
        DISABLED
    }

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Membership() {}

    public Membership(UUID tenantId, UUID userId) {
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public void deactivate() {
        this.status = Status.DISABLED;
    }

    public void activate() {
        this.status = Status.ACTIVE;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Status getStatus() {
        return status;
    }
}
