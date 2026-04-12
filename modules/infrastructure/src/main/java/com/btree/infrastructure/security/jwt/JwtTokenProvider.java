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
 * Implementação de {@link TokenProvider} usando JJWT 0.12.x com HMAC-SHA256.
 *
 * <p>O segredo configurado em {@code security.jwt.secret} deve ter no mínimo
 * 32 caracteres para atender ao requisito mínimo do HS256 (256 bits).
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    private final SecretKey signingKey;


    public JwtTokenProvider(final JwtConfig config) {
        this.signingKey = Keys.hmacShaKeyFor(
                config.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String generate(String subject, Map<String, Object> claims, Instant expiresAt) {
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public <T> T extractClaim(String token, String claimKey, Class<T> type) {
        return parseClaims(token).get(claimKey, type);
    }

    private Claims parseClaims(final String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
