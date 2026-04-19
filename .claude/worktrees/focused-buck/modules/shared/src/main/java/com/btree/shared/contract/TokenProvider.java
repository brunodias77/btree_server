package com.btree.shared.contract;

import java.time.Instant;
import java.util.Map;

/**
 * Porta para geração e validação de tokens JWT.
 * Implementação: JwtTokenProvider em infrastructure (JJWT 0.12.x).
 */
public interface TokenProvider {

    /**
     * Gera um token assinado com os claims informados.
     *
     * @param subject  identificador do subject (ex: userId)
     * @param claims   claims adicionais (ex: roles, email)
     * @param expiresAt instante de expiração
     * @return token JWT assinado
     */
    String generate(String subject, Map<String, Object> claims, Instant expiresAt);

    /**
     * Extrai o subject do token sem validar a expiração.
     */
    String extractSubject(String token);

    /**
     * Retorna true se o token estiver assinado corretamente e não expirado.
     */
    boolean isValid(String token);

    /**
     * Extrai um claim específico do token.
     */
    <T> T extractClaim(String token, String claimKey, Class<T> type);
}
