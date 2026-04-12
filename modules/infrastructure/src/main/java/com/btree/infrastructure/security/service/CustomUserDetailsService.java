package com.btree.infrastructure.security.service;


import com.btree.infrastructure.user.entity.UserJpaEntity;
import com.btree.infrastructure.user.persistence.UserJpaRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/**
 * Implementação de {@link UserDetailsService} para integração com Spring Security.
 *
 * <p>O subject do JWT é o {@code userId} (UUID). Portanto, {@code loadUserByUsername}
 * recebe um UUID em formato string e carrega o usuário pelo ID primário.
 *
 * <p>Para o fluxo de login (autenticação inicial), use {@link #loadByIdentifier(String)}
 * que aceita username <b>ou</b> e-mail.
 */
@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService, LoginUserDetailsService  {

    private final UserJpaRepository userJpaRepository;

    public CustomUserDetailsService(final UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }


    @Override
    public UserDetails loadByIdentifier(String identifier) throws UsernameNotFoundException {
        final UserJpaEntity entity = userJpaRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + identifier));

        return buildUserDetails(entity);
    }

    /**
     * Carrega pelo userId (UUID string) — chamado pelo {@link JwtAuthenticationFilter}
     * após extrair o subject do token.
     */
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {

        final UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Identificador de usuário inválido: " + userId);
        }

        final UserJpaEntity entity = userJpaRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado com id: " + userId));

        return buildUserDetails(entity);
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private UserDetails buildUserDetails(final UserJpaEntity entity) {
        final Collection<GrantedAuthority> authorities = entity.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(
                        role.getName().startsWith("ROLE_")
                                ? role.getName()
                                : "ROLE_" + role.getName()))
                .toList();

        return User.builder()
                .username(entity.getId().toString())   // subject = userId
                .password(entity.getPasswordHash() != null ? entity.getPasswordHash() : "")
                .authorities(authorities)
                .accountLocked(entity.isAccountLocked())
                .disabled(!entity.isEnabled())
                .accountExpired(false)
                .credentialsExpired(false)
                .build();
    }
}
