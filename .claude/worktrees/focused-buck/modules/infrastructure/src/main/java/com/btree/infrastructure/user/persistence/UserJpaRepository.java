package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByUsername(String username);

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Verifica existência de username ignorando capitalização.
     * Usada para validar unicidade case-insensitive no registro (UC-01).
     */
    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Verifica existência de e-mail ignorando capitalização.
     * Usada para validar unicidade case-insensitive no registro (UC-01).
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Busca por username OU email — utilizado no login onde o usuário
     * pode autenticar com qualquer um dos dois identificadores.
     */
    @Query("SELECT u FROM UserJpaEntity u WHERE u.username = :identifier OR u.email = :identifier")
    Optional<UserJpaEntity> findByUsernameOrEmail(@Param("identifier") String identifier);
}
