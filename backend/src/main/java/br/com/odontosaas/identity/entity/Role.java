package br.com.odontosaas.identity.entity;

import br.com.odontosaas.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Papel de uma clínica (conjunto de permissões em {@code role_permissions}). */
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Role() {}

    public Role(UUID tenantId, String code, String name, boolean systemRole) {
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.systemRole = systemRole;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isSystemRole() {
        return systemRole;
    }
}
