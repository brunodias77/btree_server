package com.btree.infrastructure.security.service;


import com.btree.shared.contract.TokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Implementação de {@link TokenHasher} usando SHA-256 para tokens opacos (refresh tokens).
 *
 * <p>Diferente do BCrypt, SHA-256 é determinístico — permitindo lookup por hash na
 * tabela {@code users.sessions} sem necessidade de comparação em memória.
 */
@Component
public class Sha256TokenHasher implements TokenHasher {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String generate() {
        final var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public String hash(final String token) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            final var hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
