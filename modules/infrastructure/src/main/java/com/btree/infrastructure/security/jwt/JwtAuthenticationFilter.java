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
 * Filtro de autenticação JWT — executa exatamente uma vez por requisição HTTP.
 *
 * <p>Estende {@link OncePerRequestFilter} para garantir que, mesmo em cadeias de
 * filtros que redirecionam internamente, este filtro não seja invocado mais de uma
 * vez na mesma requisição.
 *
 * <h3>Fluxo de execução</h3>
 * <ol>
 *   <li>Extrai o token do header {@code Authorization: Bearer <token>}.</li>
 *   <li>Valida assinatura e expiração via {@link JwtTokenProvider#isValid}.</li>
 *   <li>Extrai o {@code userId} (UUID string) do campo {@code sub} (subject) do token.</li>
 *   <li>Carrega o {@link UserDetails} do banco via {@link CustomUserDetailsService#loadUserByUsername}.</li>
 *   <li>Cria um {@link UsernamePasswordAuthenticationToken} com as authorities do usuário
 *       e o registra no {@link SecurityContextHolder} — autenticando o contexto da thread.</li>
 *   <li>Repassa a requisição para o próximo filtro da cadeia.</li>
 * </ol>
 *
 * <p>Se o token estiver ausente, inválido ou expirado, <b>nenhuma exceção é lançada</b>:
 * a requisição continua sem autenticação, e o Spring Security retornará HTTP 401
 * automaticamente para rotas protegidas.
 *
 * <h3>Posição na cadeia de filtros</h3>
 * <p>Registrado em {@code SecurityConfig} para rodar <em>antes</em> do
 * {@code UsernamePasswordAuthenticationFilter} padrão do Spring Security. Isso garante
 * que o contexto de segurança já esteja populado quando o framework for verificar o acesso.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Prefixo padrão do header Authorization para tokens Bearer (inclui o espaço). */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Nome do header HTTP que transporta o token JWT. */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Responsável por gerar, validar e inspecionar tokens JWT. */
    private final JwtTokenProvider jwtTokenProvider;

    /** Carrega os detalhes do usuário (roles, status da conta) a partir do banco de dados. */
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            final JwtTokenProvider jwtTokenProvider,
            final CustomUserDetailsService userDetailsService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Lógica principal do filtro: tenta autenticar a requisição via token JWT.
     *
     * <p>O bloco {@code try/catch} captura qualquer exceção durante a autenticação
     * (ex.: usuário deletado após emissão do token, UUID malformado) e limpa o
     * contexto de segurança para garantir que a requisição não passe como autenticada
     * em caso de erro parcial.
     *
     * @param request     requisição HTTP recebida
     * @param response    resposta HTTP a ser enviada
     * @param filterChain próximo filtro na cadeia
     */
    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {

        // Extrai o token bruto do header Authorization (sem o prefixo "Bearer ").
        final String token = extractToken(request);

        // Só tenta autenticar se o token existir e for criptograficamente válido.
        if (StringUtils.hasText(token) && jwtTokenProvider.isValid(token)) {
            try {
                // O subject do JWT é o userId (UUID) do usuário autenticado.
                final String userId = jwtTokenProvider.extractSubject(token);

                // Carrega o UserDetails para obter as authorities (roles) e o status da conta.
                final UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                // Cria o token de autenticação do Spring Security.
                // O segundo argumento (credentials) é null: em APIs stateless JWT,
                // não há credenciais (senha) a transportar após o login inicial.
                final var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );

                // Adiciona metadados da requisição (IP, session ID) ao objeto de autenticação
                // para fins de auditoria e rastreamento (ex.: logs de acesso).
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Registra a autenticação no contexto da thread atual.
                // A partir daqui, a requisição é considerada autenticada pelo Spring Security.
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception ex) {
                // Falha controlada: token válido criptograficamente, mas usuário não encontrado
                // ou conta com estado inválido. Limpa o contexto para evitar autenticação parcial.
                log.debug("Não foi possível autenticar via JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        // Sempre passa para o próximo filtro, mesmo sem autenticação.
        // Rotas protegidas serão barradas pelo AccessDecisionManager do Spring Security.
        filterChain.doFilter(request, response);
    }

    /**
     * Extrai o token JWT bruto do header {@code Authorization}.
     *
     * <p>O formato esperado é: {@code Authorization: Bearer eyJhbGci...}
     * O prefixo {@code "Bearer "} é removido antes de retornar o token puro.
     *
     * @param request requisição HTTP de onde o header será lido
     * @return o token JWT sem o prefixo, ou {@code null} se o header estiver ausente
     *         ou não seguir o formato Bearer
     */
    private String extractToken(final HttpServletRequest request) {
        final String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            // Remove o prefixo "Bearer " (7 caracteres) para obter apenas o token.
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Indica ao framework que este filtro deve ser ignorado para certas rotas.
     *
     * <p>Rotas de autenticação, monitoramento e documentação são excluídas da
     * verificação JWT para não gerar overhead desnecessário. A proteção dessas
     * rotas (ou a falta dela) é gerenciada pela whitelist em {@code SecurityConfig}.
     *
     * @param request requisição recebida
     * @return {@code true} se o filtro deve ser pulado para a rota da requisição
     */
    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")   // login, registro, refresh, etc.
                || path.startsWith("/actuator/")  // health check e métricas
                || path.startsWith("/swagger-ui/") // interface Swagger
                || path.startsWith("/v3/api-docs"); // spec OpenAPI
    }
}
