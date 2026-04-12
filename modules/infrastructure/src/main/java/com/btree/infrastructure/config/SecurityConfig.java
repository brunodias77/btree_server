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
 * A muralha principal de segurança da infraestrutura Spring.
 * 
 * <p>Centraliza as seguintes configurações Críticas:
 * <ul>
 *     <li>Desativação do CSRF e Sessão de Servidor para assumir política puramente Stateless.</li>
 *     <li>Filtramento minucioso interceptando requisições REST pela checagem de Auth do tipo Bearer JWT 
 *         ({@link JwtAuthenticationFilter}) antes do processamento principal.</li>
 *     <li>Mapeamento de rotas "White List", ex: Autenticação, Saúde do Actuator e Documentação.</li>
 *     <li>Fornece o provedor Criptográfico Universal: {@code BCryptPasswordEncoder}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Rotas públicas que não requerem envio de token JWT no cabeçalho
    // (cadastro, login, fluxo de senha, OAuth2, health check e documentação).
    private static final String[] PUBLIC_ROUTES = {
            "/v1/auth/register",
            "/v1/auth/login",
            "/v1/auth/refresh",
            "/v1/auth/verify-email",
            "/v1/auth/logout",
            "/v1/auth/password/forgot",
            "/v1/auth/social/**",
            "/v1/auth/2fa/verify",
            "/actuator/health",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(final JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http, final AuthenticationEntryPoint authenticationEntryPoint, final AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // 1. Desativa a proteção CSRF: Por usarmos arquitetura Stateless+REST (onde a sessão não fica guardada via Cookie), CSRF não é um vetor de ataque realista.
                .csrf(AbstractHttpConfigurer::disable)
                
                // 2. Desativa as sessões HTTP de Servidor (HttpSession): Obriga todo request a ser independente e se provar enviando o Token JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // 3. Libera o tráfego nas rotas contidas no array PUBLIC_ROUTES e impõe barreira em TODO resto da API que não for listada.
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_ROUTES).permitAll().anyRequest().authenticated())
                
                // 4. Injeta manipuladores estilizados para lidar graciosamente com retornos de Erro HTTP 401 (Usuário não logado) e 403 (Usuário logado mas barrado).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler))
                
                // 5. Instala na máquina o filtro de interceptação customizado (JwtAuthenticationFilter) exigindo que opere sempre ANTES do UsernamePasswordAuthenticationFilter do framework.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expõe o {@link AuthenticationManager} como bean para uso nos Use Cases
     * de autenticação (ex: {@code AuthenticateUserUseCase}).
     */
    @Bean
    public AuthenticationManager authenticationManager(
            final AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
