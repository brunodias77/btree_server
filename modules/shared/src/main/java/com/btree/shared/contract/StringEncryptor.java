package com.btree.shared.contract;

/**
 * Contrato para criptografia simétrica reversível de strings sensíveis em repouso.
 * Diferente de {@link TokenHasher} (one-way), este contrato permite decrypt.
 */
public interface StringEncryptor {
    String encrypt(String value);
    String decrypt(String value);
}
