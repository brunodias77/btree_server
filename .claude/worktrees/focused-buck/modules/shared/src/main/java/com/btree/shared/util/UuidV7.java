package com.btree.shared.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Geração de UUIDs v7 (time-ordered) sem dependência de Spring.
 *
 * <p>UUID v7 encoda o timestamp Unix em milissegundos nos 48 bits mais significativos,
 * garantindo ordenação cronológica e melhor localidade de inserção em índices B-tree
 * em comparação ao UUID v4 aleatório.
 *
 * <p>Formato: {@code tttttttt-tttt-7xxx-yxxx-xxxxxxxxxxxx}
 * <ul>
 *   <li>48 bits: timestamp Unix em ms</li>
 *   <li>4 bits: versão (0111 = 7)</li>
 *   <li>12 bits: aleatório</li>
 *   <li>2 bits: variante (10)</li>
 *   <li>62 bits: aleatório</li>
 * </ul>
 *
 * <p>Usado em {@link com.btree.shared.domain.DomainEvent} e
 * {@link com.btree.shared.event.IntegrationEvent} para garantir que eventId seja
 * time-ordered sem exigir injeção de dependência.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    /** Gera um novo UUID v7. */
    public static UUID generate() {
        final long now = System.currentTimeMillis();

        // 48 bits de timestamp + 4 bits de versão + 12 bits aleatórios
        final long msb = (now << 16)
                | (0x7000L)                          // versão 7
                | (RANDOM.nextLong() & 0x0FFFL);     // 12 bits aleatórios

        // 2 bits de variante (10) + 62 bits aleatórios
        final long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL)
                | 0x8000_0000_0000_0000L;             // variante RFC 4122

        return new UUID(msb, lsb);
    }
}
