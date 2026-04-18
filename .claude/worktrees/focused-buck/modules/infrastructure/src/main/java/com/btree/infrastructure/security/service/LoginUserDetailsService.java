package com.btree.infrastructure.security.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Contrato para carregamento de {@link UserDetails} pelo identificador de login
 * (username <b>ou</b> e-mail), separado do {@link org.springframework.security.core.userdetails.UserDetailsService}
 * que usa o {@code userId} (UUID) como chave para validação de JWT.
 *
 * <p>Mantém a separação entre dois fluxos distintos:
 * <ul>
 *   <li>{@code UserDetailsService#loadUserByUsername(userId)} — chamado pelo filtro JWT após autenticação</li>
 *   <li>{@code LoginUserDetailsService#loadByIdentifier(identifier)} — chamado no login inicial</li>
 * </ul>
 */
public interface LoginUserDetailsService {

    UserDetails loadByIdentifier(String identifier) throws UsernameNotFoundException;
}
