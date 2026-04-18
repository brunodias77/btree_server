package com.btree.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que registra método, URI (com query string), status HTTP e tempo de
 * resposta de cada requisição.
 *
 * <p>Executa com {@code @Order(1)} para ser o primeiro da cadeia, garantindo
 * que o tempo medido cubra todos os filtros e handlers subsequentes.
 *
 * <p>Nível de log por status HTTP:
 * <ul>
 *   <li>5xx → {@code ERROR}</li>
 *   <li>4xx → {@code WARN}</li>
 *   <li>2xx/3xx → {@code INFO}</li>
 * </ul>
 *
 * <p>Rotas de infraestrutura (actuator, swagger, favicon) são ignoradas para
 * evitar ruído nos logs de produção.
 */
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /**
     * Mede o tempo total da requisição e loga o resultado ao final.
     *
     * <p>O cronômetro inicia antes de delegar para a cadeia de filtros
     * ({@code filterChain.doFilter}) e para no {@code finally}, garantindo
     * que o tempo seja registrado mesmo em caso de exceção. O bloco
     * {@code finally} evita que uma exceção no handler impeça o log.
     *
     * <p>O nível de log varia conforme o status HTTP para facilitar a
     * triagem em ferramentas de monitoramento:
     * <ul>
     *   <li>{@code 5xx} → {@code ERROR} — falha no servidor, requer investigação</li>
     *   <li>{@code 4xx} → {@code WARN}  — erro do cliente, útil para detectar abuso</li>
     *   <li>{@code 2xx/3xx} → {@code INFO} — operação normal</li>
     * </ul>
     *
     * <p>Formato da mensagem: {@code GET /api/v1/products?page=0 → 200 (42ms)}
     *
     * @param request     requisição HTTP recebida
     * @param response    resposta HTTP construída pelos handlers
     * @param filterChain restante da cadeia de filtros a ser executada
     */
    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        final long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            final long elapsed = System.currentTimeMillis() - start;
            final int status = response.getStatus();
            final String uri = buildUri(request);

            if (status >= 500) {
                log.error("{} {} → {} ({}ms)", request.getMethod(), uri, status, elapsed);
            } else if (status >= 400) {
                log.warn("{} {} → {} ({}ms)", request.getMethod(), uri, status, elapsed);
            } else {
                log.info("{} {} → {} ({}ms)", request.getMethod(), uri, status, elapsed);
            }
        }
    }

    /**
     * Exclui rotas de infraestrutura do logging para reduzir ruído.
     *
     * <p>Rotas excluídas:
     * <ul>
     *   <li>{@code /actuator/*} — health checks dos load balancers (alta frequência)</li>
     *   <li>{@code /swagger-ui/*} — assets estáticos da documentação (HTML, JS, CSS)</li>
     *   <li>{@code /v3/api-docs*} — spec OpenAPI em JSON/YAML</li>
     *   <li>{@code /favicon.ico} — requisição automática do navegador</li>
     * </ul>
     *
     * @param request requisição a ser avaliada
     * @return {@code true} se o filtro deve ser ignorado para esta rota
     */
    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/favicon.ico");
    }

    /**
     * Reconstrói a URI completa incluindo a query string, se presente.
     *
     * <p>Exemplo: {@code /api/v1/products} ou {@code /api/v1/products?page=0&size=20}.
     * Necessário porque {@code request.getRequestURI()} não inclui os parâmetros.
     *
     * @param request requisição de onde extrair URI e query string
     * @return URI completa para exibição no log
     */
    private static String buildUri(final HttpServletRequest request) {
        final String queryString = request.getQueryString();
        return StringUtils.hasText(queryString)
                ? request.getRequestURI() + "?" + queryString
                : request.getRequestURI();
    }
}
