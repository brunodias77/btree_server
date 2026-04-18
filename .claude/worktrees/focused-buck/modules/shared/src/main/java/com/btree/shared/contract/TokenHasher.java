package com.btree.shared.contract;

/**
 * Contrato para geração e hash determinístico de tokens opacos (ex: refresh tokens).
 *
 * <p>Diferente de {@link PasswordHasher} (BCrypt — não-determinístico), este contrato
 * usa SHA-256 para permitir lookup por hash na persistência.
 */
public interface TokenHasher {

    /** Gera um token opaco seguro (64 hex chars). */
    String generate();

    /** Retorna o hash SHA-256 do token em formato hexadecimal. */
    String hash(String token);
}
