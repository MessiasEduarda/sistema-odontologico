package br.com.odontosaas.identity.repository;

import br.com.odontosaas.identity.entity.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<Membership> findByTenantIdOrderByCreatedAt(UUID tenantId);
}
