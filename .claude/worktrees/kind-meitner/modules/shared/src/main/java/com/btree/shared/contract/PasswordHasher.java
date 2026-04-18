package com.btree.shared.contract;

/**
 * Porta para hash e verificação de senhas.
 * Implementação: BCryptPasswordHasher em infrastructure.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashedPassword);
}
