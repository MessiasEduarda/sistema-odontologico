package br.com.odontosaas.identity.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByTenantIdAndEmailAndStatus(UUID tenantId, String email, Invitation.Status status);

    List<Invitation> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, Invitation.Status status);
}
