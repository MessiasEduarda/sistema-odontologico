package br.com.odontosaas.identity.repository;

import br.com.odontosaas.identity.entity.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Toda consulta da clínica filtra EXPLICITAMENTE por {@code tenantId} (vindo do contexto do servidor). O RLS desta
 * tabela também deixa o usuário ver os próprios vínculos em outras clínicas, então o filtro explícito é obrigatório
 * para operações da clínica ativa.
 */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<Membership> findByTenantIdOrderByCreatedAt(UUID tenantId);
}
