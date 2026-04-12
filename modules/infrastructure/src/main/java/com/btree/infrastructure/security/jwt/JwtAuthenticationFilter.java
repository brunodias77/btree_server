package com.btree.infrastructure.security.jwt;

import com.btree.infrastructure.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticação JWT — executa uma vez por requisição.
 *
 * <p>Fluxo:
 * <ol>
 *   <li>Extrai o token do header {@code Authorization: Bearer <token>}</li>
 *   <li>Valida assinatura e expiração via {@link JwtTokenProvider}</li>
 *   <li>Extrai o {@code userId} do subject do token</li>
 *   <li>Carrega {@link UserDetails} via {@link CustomUserDetailsService}</li>
 *   <li>Registra a autenticação no {@link SecurityContextHolder}</li>
 * </ol>
 *
 * <p>Se o token estiver ausente, inválido ou expirado, a requisição continua
 * sem autenticação — o Spring Security retornará 401 para rotas protegidas.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            final JwtTokenProvider jwtTokenProvider,
            final CustomUserDetailsService userDetailsService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.isValid(token)) {
            try {
                final String userId = jwtTokenProvider.extractSubject(token);
                final UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                final var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                log.debug("Não foi possível autenticar via JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(final HttpServletRequest request) {
        final String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs");
    }
}
