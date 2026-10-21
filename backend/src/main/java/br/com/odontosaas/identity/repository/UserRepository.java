package br.com.odontosaas.identity.repository;

import br.com.odontosaas.identity.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** A coluna é citext: a comparação já ignora maiúsculas/minúsculas. */
    Optional<User> findByEmail(String email);

    boolean existsByPlatformRole(String platformRole);
}
