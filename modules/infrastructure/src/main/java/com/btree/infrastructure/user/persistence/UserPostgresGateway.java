package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.User;
import com.btree.domain.user.gateway.UserGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.user.entity.UserJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementação de {@link UserGateway} com Spring Data JPA / PostgreSQL.
 *
 * <p>Convenções:
 * <ul>
 *   <li>{@code save} e {@code update} delegam para {@link UserJpaRepository#save} — o Spring Data
 *       decide INSERT vs UPDATE com base na presença do ID na sessão JPA.</li>
 *   <li>O campo {@code version} em {@link UserJpaEntity} usa {@code @Version} para
 *       optimistic locking; conflitos disparam {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</li>
 *   <li>A conversão domínio ↔ JPA fica encapsulada em {@link UserJpaEntity#from} e
 *       {@link UserJpaEntity#toAggregate()}, mantendo o gateway enxuto.</li>
 * </ul>
 */
@Component
@Transactional
public class UserPostgresGateway implements UserGateway {

    private final UserJpaRepository userJpaRepository;
    private final RoleJpaRepository roleJpaRepository;

    public UserPostgresGateway(
            final UserJpaRepository userJpaRepository,
            final RoleJpaRepository roleJpaRepository
    ) {
        this.userJpaRepository = userJpaRepository;
        this.roleJpaRepository = roleJpaRepository;
    }

    // ── Escritas (Write Operations) ───────────────────────────────────────────

    @Override
    public User save(User user) {
        return userJpaRepository
                .save(UserJpaEntity.from(user))
                .toAggregate();
    }

    @Override
    public User update(User user) {
        final var entity = userJpaRepository.findById(user.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(User.class, user.getId().getValue()));
        entity.updateFrom(user);
        return userJpaRepository.save(entity).toAggregate();
    }

    // ── Buscas Booleanas (Checks) ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(final String username) {
        return userJpaRepository.existsByUsernameIgnoreCase(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(final String email) {
        return userJpaRepository.existsByEmailIgnoreCase(email);
    }

    // ── Buscas por Entidade (Queries) ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(final String email) {
        return userJpaRepository
                .findByEmail(email)
                .map(UserJpaEntity::toAggregate);
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(final UserId id) {
        return userJpaRepository
                .findById(id.getValue())
                .map(UserJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsernameOrEmail(final String identifier) {
        return userJpaRepository
                .findByUsernameOrEmail(identifier)
                .map(UserJpaEntity::toAggregate);
    }

    // ── Relações (Relationships) ──────────────────────────────────────────────

    /**
     * Atribui uma role ao usuário.
     *
     * <p>Busca a {@link com.btree.infrastructure.user.entity.RoleJpaEntity} pelo nome
     * e adiciona à coleção do usuário. A relação é persistida via cascade da tabela
     * {@code users.user_roles}.
     *
     * @throws NotFoundException se o usuário ou a role não forem encontrados.
     */
    @Override
    public void assignRole(UserId userId, String roleName) {
        final var userEntity = userJpaRepository.findById(userId.getValue())
                .orElseThrow(() -> NotFoundException.with(User.class, userId.getValue()));

        final var roleEntity = roleJpaRepository.findByName(roleName)
                .orElseThrow(() -> NotFoundException.with("Role '%s' não encontrada".formatted(roleName)));

        userEntity.getRoles().add(roleEntity);
        userJpaRepository.save(userEntity);
    }


}
