package br.com.odontosaas.tenancy;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsBySlugIgnoreCase(String slug);

    java.util.Optional<Tenant> findBySlugIgnoreCase(String slug);
}
