package br.com.odontosaas.identity.entity;

import br.com.odontosaas.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    public enum Status {
        ACTIVE,
        DISABLED
    }

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "platform_role")
    private String platformRole;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected User() {}

    public User(String email, String name, String passwordHash) {
        this.email = normalizeEmail(email);
        this.name = name.strip();
        this.passwordHash = passwordHash;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordChangedAt = Instant.now();
    }

    public void grantPlatformAdmin() {
        this.platformRole = "PLATFORM_ADMIN";
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isPlatformAdmin() {
        return "PLATFORM_ADMIN".equals(platformRole);
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
