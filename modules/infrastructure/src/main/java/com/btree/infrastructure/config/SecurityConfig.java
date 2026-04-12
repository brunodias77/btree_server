package com.btree.infrastructure.config;

import com.btree.infrastructure.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração central de segurança da aplicação (Spring Security).
 *
 * <p>Esta classe é o ponto único que define <b>quem pode acessar o quê</b>,
 * <b>como as credenciais são verificadas</b> e <b>como os erros de autenticação
 * são comunicados ao cliente</b>. Qualquer mudança de política de segurança
 * (novas rotas públicas, estratégia de sessão, algoritmo de hash) deve passar
 * por aqui.
 *
 * <h3>Anotações da classe</h3>
 * <ul>
 *   <li>{@code @EnableWebSecurity} — ativa e substitui a auto-configuração padrão
 *       do Spring Security, dando controle total sobre a cadeia de filtros HTTP.</li>
 *   <li>{@code @EnableMethodSecurity} — habilita as anotações de segurança em nível
 *       de método ({@code @PreAuthorize}, {@code @PostAuthorize}, {@code @Secured})
 *       para controle de acesso granular nos Use Cases e Controllers.</li>
 * </ul>
 *
 * <h3>Responsabilidades centralizadas</h3>
 * <ul>
 *   <li>Desativação do CSRF e das sessões de servidor (política puramente Stateless).</li>
 *   <li>Filtragem de requisições via {@link JwtAuthenticationFilter} (Bearer JWT).</li>
 *   <li>Mapeamento da "whitelist" de rotas públicas (auth, health, docs).</li>
 *   <li>Tratamento elegante de erros HTTP 401 e 403.</li>
 *   <li>Fornecimento do encoder criptográfico ({@code BCrypt}) como bean global.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Rotas que <b>não</b> exigem token JWT no cabeçalho {@code Authorization}.
     *
     * <p>Inclui todos os endpoints do fluxo de autenticação (registro, login, refresh,
     * verificação de e-mail, logout e recuperação de senha), o login social via OAuth2,
     * a verificação de 2FA, o health check do Actuator e a documentação Swagger.
     *
     * <p><b>Atenção:</b> qualquer rota adicionada aqui fica completamente desprotegida.
     * Endpoints que apenas mudam o método de autenticação (ex.: chave de API) NÃO
     * devem ser listados aqui; devem ter seu próprio filtro dedicado.
     */
    private static final String[] PUBLIC_ROUTES = {
            "/v1/auth/register",
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/verify-email",
            "/v1/auth/logout",
            "/v1/auth/password/forgot",
            "/v1/auth/social/**",   // padrão wildcard para todos os provedores OAuth2
            "/v1/auth/2fa/verify",
            "/actuator/health",     // health check sem auth para load balancers e orquestradores
            "/swagger-ui/**",       // UI do Swagger (HTML + JS + CSS)
            "/v3/api-docs/**"       // spec OpenAPI 3 em JSON/YAML
    };

    /** Filtro customizado que extrai e valida o Bearer JWT de cada requisição. */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(final JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Constrói e registra a cadeia de filtros de segurança HTTP.
     *
     * <p>A cadeia é formada por uma sequência ordenada de configurações que são
     * aplicadas a <b>toda</b> requisição recebida pelo servidor:
     *
     * <ol>
     *   <li><b>CSRF desativado</b> — Em APIs REST stateless com autenticação JWT,
     *       CSRF não é um vetor viável: ataques CSRF dependem de cookies de sessão
     *       que o navegador envia automaticamente. Como usamos Bearer tokens no
     *       cabeçalho {@code Authorization} (não cookies), o ataque não se aplica.</li>
     *
     *   <li><b>Sessões desativadas ({@code STATELESS})</b> — O servidor nunca cria
     *       nem usa {@code HttpSession}. Cada requisição precisa se auto-identificar
     *       via token JWT. Isso permite escalar horizontalmente sem sincronização
     *       de sessões entre instâncias.</li>
     *
     *   <li><b>Controle de acesso por rota</b> — As rotas em {@link #PUBLIC_ROUTES}
     *       recebem {@code permitAll()}: qualquer um acessa sem autenticação. Todas
     *       as demais rotas exigem {@code authenticated()}: o usuário precisa ter um
     *       token JWT válido (preenchido no {@code SecurityContext} pelo filtro JWT).</li>
     *
     *   <li><b>Tratamento de exceções de segurança</b> — Dois handlers são registrados:
     *       <ul>
     *         <li>{@code authenticationEntryPoint}: invocado quando o usuário não está
     *             autenticado (sem token ou token inválido) → HTTP 401 Unauthorized.</li>
     *         <li>{@code accessDeniedHandler}: invocado quando o usuário está autenticado
     *             mas não tem permissão para o recurso → HTTP 403 Forbidden.</li>
     *       </ul>
     *       Ambos são injetados como beans, permitindo substituição em testes ou
     *       perfis específicos via {@code @ConditionalOnMissingBean}.</li>
     *
     *   <li><b>Filtro JWT posicionado antes do filtro padrão</b> — O
     *       {@link JwtAuthenticationFilter} é inserido <em>antes</em> do
     *       {@link UsernamePasswordAuthenticationFilter} do Spring Security. Isso
     *       garante que, se um token JWT válido for encontrado, o contexto de
     *       segurança já estará populado e o filtro de usuário/senha não tentará
     *       uma autenticação desnecessária.</li>
     * </ol>
     *
     * @param http                     builder da cadeia de filtros fornecido pelo Spring Security
     * @param authenticationEntryPoint handler para respostas HTTP 401
     * @param accessDeniedHandler      handler para respostas HTTP 403
     * @return cadeia de filtros configurada e pronta para uso
     */
    @Bean
    public SecurityFilterChain filterChain(
            final HttpSecurity http,
            final AuthenticationEntryPoint authenticationEntryPoint,
            final AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                // 1. Desativa CSRF: sem cookies de sessão, ataques CSRF são inviáveis em APIs JWT.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Sem sessão de servidor: cada requisição deve provar sua identidade via JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Libera PUBLIC_ROUTES sem autenticação; bloqueia todo o restante.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .anyRequest().authenticated()         // regra catch-all: tudo mais exige auth
                )

                // 4. Handlers de erro: 401 para não autenticados, 403 para sem permissão.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // 5. Filtro JWT roda antes do filtro padrão de usuário/senha do Spring Security.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Handler padrão para erros de autenticação (HTTP 401 Unauthorized).
     *
     * <p>Invocado pelo Spring Security quando uma requisição tenta acessar um recurso
     * protegido sem fornecer um token JWT válido (token ausente, expirado ou malformado).
     *
     * <p>A anotação {@code @ConditionalOnMissingBean} permite que testes de integração
     * ou perfis específicos substituam este bean por uma implementação customizada
     * (ex.: que retorne um corpo JSON estruturado com código de erro).
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        // Retorna HTTP 401 com mensagem simples.
        // TODO: substituir por resposta JSON padronizada com o corpo de erro da API.
        return (request, response, ex) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    /**
     * Handler padrão para erros de autorização (HTTP 403 Forbidden).
     *
     * <p>Invocado pelo Spring Security quando o usuário está autenticado (token válido),
     * mas não possui a role ou permissão necessária para o recurso solicitado.
     *
     * <p>A anotação {@code @ConditionalOnMissingBean} permite sobrescrição do bean
     * em testes ou quando for necessária uma resposta com corpo JSON detalhado.
     */
    @Bean
    @ConditionalOnMissingBean
    public AccessDeniedHandler accessDeniedHandler() {
        // Retorna HTTP 403 com mensagem simples.
        // TODO: substituir por resposta JSON padronizada com o corpo de erro da API.
        return (request, response, ex) ->
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
    }

    /**
     * Registra o {@link PasswordEncoder} baseado em BCrypt como bean global.
     *
     * <p>BCrypt é o algoritmo de hashing recomendado para senhas porque:
     * <ul>
     *   <li>Inclui um <b>salt aleatório</b> automaticamente em cada hash, impedindo
     *       ataques de rainbow table e garantindo que dois hashes da mesma senha sejam
     *       sempre diferentes.</li>
     *   <li>Tem <b>custo computacional ajustável</b> (fator de trabalho): à medida que
     *       o hardware evolui, o fator pode ser aumentado sem migrar senhas existentes.</li>
     *   <li>É nativamente suportado pelo Spring Security e amplamente auditado.</li>
     * </ul>
     *
     * <p>Este bean é injetado em qualquer componente que precise hashar ou verificar
     * senhas (ex.: {@code BcryptPasswordHasher} na camada de infrastructure).
     *
     * @return instância de {@code BCryptPasswordEncoder} com fator de custo padrão (10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expõe o {@link AuthenticationManager} como bean gerenciável pelo Spring.
     *
     * <p>O {@code AuthenticationManager} é o orquestrador central de autenticação do
     * Spring Security: recebe uma {@code Authentication} não autenticada e devolve uma
     * autenticada (ou lança exceção). Por padrão, ele não é exposto como bean; esta
     * declaração explícita o torna injetável nos Use Cases de autenticação
     * (ex.: {@code AuthenticateUserUseCase}) sem precisar quebrar a encapsulação da
     * configuração de segurança.
     *
     * @param config fonte de configuração de autenticação gerenciada pelo Spring
     * @return o {@code AuthenticationManager} configurado
     */
    @Bean
    public AuthenticationManager authenticationManager(
            final AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
