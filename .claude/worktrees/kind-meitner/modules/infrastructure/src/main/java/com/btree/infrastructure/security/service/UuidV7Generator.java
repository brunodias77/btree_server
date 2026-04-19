package com.btree.infrastructure.security.service;

import com.btree.shared.contract.UuidGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Gerador de UUIDs v7 (time-ordered) conforme RFC 9562.
 *
 * <p>Estrutura dos 128 bits:
 * <pre>
 *  MSB:  [63:16] unix_ts_ms (48 bits) | [15:12] version=7 | [11:0] rand_a (12 bits)
 *  LSB:  [63:62] variant=10           | [61:0]  rand_b    (62 bits)
 * </pre>
 *
 * <p>A ordenação temporal garante índices B-Tree eficientes no PostgreSQL,
 * equivalente ao uso da função {@code uuid_generate_v7()} no banco.
 */
@Component
public class UuidV7Generator implements UuidGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate() {
        return generateV7().toString();
    }

    public static UUID generateV7() {
        final long now = System.currentTimeMillis();
        final long randA = RANDOM.nextLong();
        final long randB = RANDOM.nextLong();

        // MSB: 48 bits timestamp | 4 bits version (0111) | 12 bits rand_a
        final long msb = ((now & 0xFFFFFFFFFFFFL) << 16)
                | 0x7000L
                | (randA & 0x0FFFL);

        // LSB: 2 bits variant (10) | 62 bits rand_b
        final long lsb = 0x8000000000000000L
                | (randB & 0x3FFFFFFFFFFFFFFFL);

        return new UUID(msb, lsb);
    }
}
