package com.btree.infrastructure.security.jwt;

import com.btree.infrastructure.config.JwtConfig;
import com.btree.shared.contract.TokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Implementação de {@link TokenProvider} usando a biblioteca JJWT 0.12.x
 * com o algoritmo de assinatura HMAC-SHA256 (HS256).
 *
 * <h3>Por que HMAC-SHA256 (HS256)?</h3>
 * <p>O HS256 usa uma única chave secreta compartilhada para assinar e verificar tokens.
 * É adequado para monólitos e serviços onde todas as instâncias compartilham o mesmo
 * segredo (configurado via {@code security.jwt.secret}). Para arquiteturas com múltiplos
 * serviços que precisam verificar tokens independentemente, considere migrar para RS256
 * (chaves assimétricas), onde a chave privada assina e a pública verifica.
 *
 * <h3>Requisito do segredo</h3>
 * <p>O valor de {@code security.jwt.secret} deve ter <b>no mínimo 32 bytes (256 bits)</b>
 * para atender ao requisito mínimo do HS256. Strings menores causarão erro na inicialização
 * ao chamar {@link Keys#hmacShaKeyFor}.
 *
 * <h3>Estrutura de um JWT gerado</h3>
 * <ul>
 *   <li><b>Header</b>: {@code {"alg":"HS256","typ":"JWT"}}</li>
 *   <li><b>Payload (claims)</b>: {@code sub=userId, iat=emittedAt, exp=expiresAt, + customClaims}</li>
 *   <li><b>Signature</b>: HMAC-SHA256(base64(header) + "." + base64(payload), signingKey)</li>
 * </ul>
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    /**
     * Chave criptográfica derivada do segredo configurado em {@code security.jwt.secret}.
     * Pré-calculada no construtor para evitar o custo de derivação a cada operação de
     * geração ou validação de token.
     */
    private final SecretKey signingKey;

    /**
     * Deriva a {@link SecretKey} a partir do segredo em texto plano da configuração.
     *
     * <p>A conversão {@code String → byte[] → SecretKey} é feita com {@code UTF-8}
     * para garantir comportamento consistente independente do encoding da JVM.
     * {@link Keys#hmacShaKeyFor} valida internamente que o array tem ao menos 32 bytes.
     *
     * @param config configurações JWT injetadas (secret, tempos de expiração)
     */
    public JwtTokenProvider(final JwtConfig config) {
        this.signingKey = Keys.hmacShaKeyFor(
                config.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Gera um token JWT assinado com HS256.
     *
     * <p>O {@code subject} deve ser um identificador imutável e único do usuário
     * (geralmente o {@code userId} em UUID string). Claims adicionais (ex.: roles,
     * tipo do token) são passados via {@code claims}. O token expira exatamente em
     * {@code expiresAt} — o cliente deve usar o endpoint de refresh antes disso.
     *
     * @param subject   identificador do principal (ex.: userId em UUID string)
     * @param claims    claims customizados adicionais a incluir no payload
     * @param expiresAt instante exato de expiração do token
     * @return string JWT compacta no formato {@code header.payload.signature}
     */
    @Override
    public String generate(final String subject, final Map<String, Object> claims, final Instant expiresAt) {
        return Jwts.builder()
                .subject(subject)           // campo "sub" do payload JWT
                .claims(claims)             // claims customizados (ex.: {"type":"ACCESS"})
                .issuedAt(Date.from(Instant.now()))    // campo "iat": quando o token foi emitido
                .expiration(Date.from(expiresAt))      // campo "exp": quando o token expira
                .signWith(signingKey)       // assina com HMAC-SHA256 usando a chave derivada
                .compact();                // serializa para a string JWT final
    }

    /**
     * Extrai o subject ({@code sub}) do payload do token.
     *
     * <p>No contexto desta aplicação, o subject é sempre o {@code userId} do usuário
     * em formato UUID string. Usado pelo {@link JwtAuthenticationFilter} para identificar
     * o usuário autenticado após a validação do token.
     *
     * @param token token JWT compacto
     * @return valor do campo {@code sub} do payload
     * @throws io.jsonwebtoken.JwtException se o token for inválido ou expirado
     */
    @Override
    public String extractSubject(final String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Verifica se o token é válido (assinatura correta e não expirado).
     *
     * <p>A verificação de expiração é feita automaticamente pelo JJWT durante o
     * parsing: se o campo {@code exp} for anterior ao instante atual, uma
     * {@link io.jsonwebtoken.ExpiredJwtException} é lançada internamente e capturada
     * aqui, resultando em {@code false}.
     *
     * <p>Retorna {@code false} — em vez de lançar exceção — para simplificar o código
     * do {@link JwtAuthenticationFilter}, que precisa apenas saber se continua ou não.
     *
     * @param token token JWT a verificar
     * @return {@code true} se a assinatura e a validade estiverem corretas
     */
    @Override
    public boolean isValid(final String token) {
        try {
            parseClaims(token);  // lança JwtException em caso de qualquer problema
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Captura: token malformado, assinatura inválida, token expirado, token vazio.
            return false;
        }
    }

    /**
     * Extrai um claim específico do payload do token.
     *
     * <p>Permite acessar qualquer campo custom do payload, como o tipo do token
     * ({@code "type": "ACCESS"} vs {@code "REFRESH"}) sem precisar deserializar
     * o objeto completo.
     *
     * @param token    token JWT compacto
     * @param claimKey nome do campo no payload (ex.: "type", "roles")
     * @param type     classe esperada do valor (ex.: {@code String.class}, {@code List.class})
     * @param <T>      tipo do valor retornado
     * @return o valor do claim ou {@code null} se o campo não existir no payload
     */
    @Override
    public <T> T extractClaim(final String token, final String claimKey, final Class<T> type) {
        return parseClaims(token).get(claimKey, type);
    }

    /**
     * Faz o parsing e a verificação da assinatura do token, retornando o payload de claims.
     *
     * <p>Este é o método central de todas as operações de leitura: qualquer chamada
     * que precise acessar o payload ({@link #extractSubject}, {@link #extractClaim},
     * {@link #isValid}) passa por aqui. O JJWT verifica automaticamente:
     * <ul>
     *   <li>que a assinatura bate com a {@code signingKey} configurada;</li>
     *   <li>que o token não está expirado ({@code exp < now}).</li>
     * </ul>
     *
     * @param token token JWT compacto a parsear
     * @return payload de claims do token após verificação bem-sucedida
     * @throws io.jsonwebtoken.JwtException se a assinatura for inválida ou o token expirado
     */
    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey)     // configura a chave de verificação da assinatura
                .build()
                .parseSignedClaims(token)   // faz o parse e verifica assinatura + expiração
                .getPayload();              // retorna apenas o payload (sem header e assinatura)
    }
}
