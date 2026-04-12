package com.btree.api.config;

import com.btree.api.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.time.Instant;

/**
 * Configura os handlers de exceção do Spring Security como beans Spring,
 * garantindo que erros de autenticação e autorização retornem {@link ApiError}
 * no mesmo formato JSON do restante da API.
 *
 * <p>Esses handlers são registrados no {@code SecurityFilterChain} via
 * {@code .exceptionHandling()} no módulo {@code infrastructure}.
 * Vivem aqui porque dependem de {@link ApiError}, que é do módulo {@code api}.
 *
 * <p>Por que não usar {@code @ExceptionHandler} no {@link com.btree.api.exception.GlobalExceptionHandler}?
 * O Spring Security intercepta {@code AuthenticationException} e {@code AccessDeniedException}
 * antes que cheguem ao {@code DispatcherServlet} — portanto {@code @ExceptionHandler}
 * nunca é invocado para esses dois tipos.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityExceptionConfig {

    /** Jackson ObjectMapper reutilizado do contexto Spring para serializar {@link ApiError} em JSON. */
    private final ObjectMapper objectMapper;

    /**
     * Injeta o {@link ObjectMapper} global do Spring (configurado com módulo
     * JavaTimeModule, naming strategy, etc.) para garantir que os erros de
     * segurança sejam serializados exatamente no mesmo formato que os erros
     * do {@link com.btree.api.exception.GlobalExceptionHandler}.
     *
     * @param objectMapper mapper Jackson do contexto Spring
     */
    public SecurityExceptionConfig(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Handler para erros de autenticação → HTTP 401 Unauthorized.
     *
     * <p>Invocado pelo Spring Security quando:
     * <ul>
     *   <li>Nenhum token JWT está presente no header {@code Authorization}</li>
     *   <li>O token é sintaticamente inválido ou expirado</li>
     *   <li>O {@code subject} do token não corresponde a um usuário ativo</li>
     * </ul>
     *
     * <p>A resposta usa {@code response.getWriter()} (não {@code getOutputStream()})
     * com charset UTF-8 explícito para evitar problemas de encoding com
     * caracteres acentuados na mensagem.
     *
     * <p>Este bean sobrescreve o handler padrão definido em {@code SecurityConfig}
     * graças ao {@code @ConditionalOnMissingBean} lá declarado.
     *
     * @return lambda que escreve {@link ApiError} com status 401 no response
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiError.of(401, "Unauthorized",
                            "Autenticação necessária.",
                            request.getRequestURI(),
                            Instant.now()));
        };
    }

    /**
     * Handler para erros de autorização → HTTP 403 Forbidden.
     *
     * <p>Invocado pelo Spring Security quando o usuário está autenticado
     * (token válido no {@code SecurityContext}), mas não possui a role ou
     * permissão necessária para o recurso solicitado. Cenários comuns:
     * <ul>
     *   <li>Usuário com role {@code CUSTOMER} tenta acessar endpoint {@code ADMIN}</li>
     *   <li>Método protegido com {@code @PreAuthorize("hasRole('ADMIN')")} falha</li>
     * </ul>
     *
     * <p>A mensagem genérica {@code "Acesso negado."} não revela qual permissão
     * está faltando, evitando enumeração de privilégios por atacantes.
     *
     * @return lambda que escreve {@link ApiError} com status 403 no response
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiError.of(403, "Forbidden",
                            "Acesso negado.",
                            request.getRequestURI(),
                            Instant.now()));
        };
    }
}
