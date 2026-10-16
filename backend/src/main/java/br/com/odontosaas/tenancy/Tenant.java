package br.com.odontosaas.tenancy;

import br.com.odontosaas.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Clínica (organização) da plataforma. Tabela global: não pertence a outro tenant. */
@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    public enum Status {
        ACTIVE,
        SUSPENDED,
        CANCELLED
    }

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String slug;

    @Column(length = 18)
    private String document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "time_zone", nullable = false)
    private String timeZone = "America/Sao_Paulo";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Tenant() {}

    public Tenant(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
