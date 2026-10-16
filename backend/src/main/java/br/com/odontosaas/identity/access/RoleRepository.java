package br.com.odontosaas.identity.access;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);

    List<Role> findByTenantIdAndCodeIn(UUID tenantId, Collection<String> codes);

    List<Role> findByTenantIdOrderByName(UUID tenantId);
}
