package com.btree.shared.contract;

/**
 * Porta para geração de UUIDs.
 * Implementação padrão: UuidV7Generator em infrastructure, usando
 * a função PostgreSQL {@code uuid_generate_v7()} ou geração local time-ordered.
 */
public interface UuidGenerator {

    String generate();
}
