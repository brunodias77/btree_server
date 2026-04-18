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
 * Implementação de {@link UserDetailsService} que integra o domínio de usuários
 * com o mecanismo de autenticação do Spring Security.
 *
 * <p>Esta classe serve dois propósitos distintos, mapeados para dois fluxos diferentes:
 *
 * <h3>1. Autenticação por Token JWT (requisições autenticadas)</h3>
 * <p>Chamada pelo {@link com.btree.infrastructure.security.jwt.JwtAuthenticationFilter}
 * após extrair o {@code sub} (subject) do token JWT. O subject neste projeto é sempre
 * o {@code userId} em formato UUID string. Por isso, {@link #loadUserByUsername(String)}
 * recebe um UUID e busca pelo ID primário — <b>não pelo username</b>, apesar do nome
 * do método (herança da interface do Spring Security).
 *
 * <h3>2. Autenticação por Credenciais (login inicial)</h3>
 * <p>Chamada pelo Use Case de login. O método {@link #loadByIdentifier(String)} aceita
 * tanto username quanto e-mail, permitindo que o usuário faça login com qualquer um dos dois.
 *
 * <h3>Por que o subject do JWT é o userId e não o username?</h3>
 * <p>Usar o UUID como subject garante estabilidade: se o usuário mudar de username ou
 * e-mail, tokens emitidos anteriormente continuam válidos (o UUID nunca muda). Além disso,
 * o UUID não expõe informações pessoais no token (mesmo que decodificado via Base64).
 *
 * <h3>Transações</h3>
 * <p>A classe é anotada com {@code @Transactional(readOnly = true)}: todos os métodos
 * de leitura do banco (sem exceção) participam de uma transação somente-leitura, o que
 * desativa o dirty-checking do Hibernate e permite otimizações de réplica de leitura.
 */
@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService, LoginUserDetailsService {

    private final UserJpaRepository userJpaRepository;

    public CustomUserDetailsService(final UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    /**
     * Carrega o usuário pelo {@code identifier} para o fluxo de login por credenciais.
     *
     * <p>O {@code identifier} pode ser o {@code username} ou o {@code e-mail} do usuário.
     * A query {@code findByUsernameOrEmail} trata os dois casos de forma transparente.
     * Usado pelo Use Case de autenticação antes de verificar a senha.
     *
     * @param identifier username ou e-mail do usuário que está tentando fazer login
     * @return {@link UserDetails} pronto para verificação de senha pelo Spring Security
     * @throws UsernameNotFoundException se nenhum usuário for encontrado com o identificador
     */
    @Override
    public UserDetails loadByIdentifier(final String identifier) throws UsernameNotFoundException {
        final UserJpaEntity entity = userJpaRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + identifier));

        return buildUserDetails(entity);
    }

    /**
     * Carrega o usuário pelo {@code userId} (UUID string) — fluxo de requisições autenticadas via JWT.
     *
     * <p>Apesar do nome herdado da interface ({@code loadUserByUsername}), este método
     * recebe um UUID e busca pelo ID primário da entidade. Isso ocorre porque o campo
     * {@code sub} (subject) do token JWT armazena o {@code userId}, não o username.
     *
     * <p>A conversão {@code String → UUID} é validada explicitamente para retornar uma
     * mensagem de erro clara quando o token contiver um subject malformado (ex.: token
     * de um sistema externo ou corrompido).
     *
     * @param userId UUID do usuário em formato string (extraído do campo {@code sub} do JWT)
     * @return {@link UserDetails} com as authorities e o status da conta
     * @throws UsernameNotFoundException se o UUID for inválido ou nenhum usuário for encontrado
     */
    @Override
    public UserDetails loadUserByUsername(final String userId) throws UsernameNotFoundException {

        // Valida o formato do UUID antes de consultar o banco.
        final UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            // O subject do JWT não é um UUID válido — token inválido ou de sistema externo.
            throw new UsernameNotFoundException("Identificador de usuário inválido: " + userId);
        }

        final UserJpaEntity entity = userJpaRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado com id: " + userId));

        return buildUserDetails(entity);
    }

    // ── Private ──────────────────────────────────────────────────────────────

    /**
     * Constrói o objeto {@link UserDetails} do Spring Security a partir da entidade JPA.
     *
     * <p>Detalhe importante: o campo {@code username} do {@link UserDetails} é preenchido
     * com o {@code userId} (UUID) — não com o username do domínio. Isso mantém consistência
     * com o subject do JWT e garante que o {@code SecurityContext} identifique o usuário
     * pelo seu ID imutável.
     *
     * <h3>Mapeamento de roles</h3>
     * <p>O Spring Security exige que as authorities sigam o padrão {@code ROLE_<NOME>}.
     * A conversão garante que roles salvas no banco com ou sem o prefixo {@code "ROLE_"}
     * sejam sempre normalizadas corretamente (ex.: {@code "ADMIN"} → {@code "ROLE_ADMIN"},
     * {@code "ROLE_USER"} → {@code "ROLE_USER"} sem duplicar o prefixo).
     *
     * <h3>Campos de estado da conta</h3>
     * <ul>
     *   <li>{@code accountLocked}: bloqueio manual por admin ou por tentativas excessivas.</li>
     *   <li>{@code disabled}: conta desativada (ex.: e-mail não verificado).</li>
     *   <li>{@code accountExpired} / {@code credentialsExpired}: não utilizados, sempre {@code false}.</li>
     * </ul>
     *
     * @param entity entidade JPA do usuário carregada do banco
     * @return {@link UserDetails} pronto para uso pelo Spring Security
     */
    private UserDetails buildUserDetails(final UserJpaEntity entity) {
        // Mapeia as roles do banco para GrantedAuthority, garantindo o prefixo "ROLE_".
        final Collection<GrantedAuthority> authorities = entity.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(
                        role.getName().startsWith("ROLE_")
                                ? role.getName()              // já tem o prefixo: usa como está
                                : "ROLE_" + role.getName()))  // adiciona o prefixo obrigatório
                .toList();

        return User.builder()
                .username(entity.getId().toString())  // userId (UUID) como identificador principal
                .password(entity.getPasswordHash() != null ? entity.getPasswordHash() : "")
                // "" como senha para usuários sem hash (ex.: login social via OAuth2).
                .authorities(authorities)
                .accountLocked(entity.isAccountLocked())  // bloqueia se a conta foi travada
                .disabled(!entity.isEnabled())            // desativa se e-mail não verificado, etc.
                .accountExpired(false)      // não utilizado neste domínio
                .credentialsExpired(false)  // não utilizado neste domínio
                .build();
    }
}
