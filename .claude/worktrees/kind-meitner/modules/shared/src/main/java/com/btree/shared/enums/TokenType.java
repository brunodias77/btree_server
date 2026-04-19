package com.btree.shared.enums;

/**
 * Tipo de token de verificação de usuário.
 * <p>
 * Mapeado para o tipo PostgreSQL {@code shared.token_type}.
 */
public enum TokenType {
    EMAIL_VERIFICATION("Verificação de e-mail"),
    PASSWORD_RESET("Redefinição de senha"),
    TWO_FACTOR("Autenticação de dois fatores"),
    TWO_FACTOR_SETUP("Setup de Autenticação de dois fatores"),
    MAGIC_LINK("Link mágico"),
    ACCOUNT_UNLOCK("Desbloqueio de conta"),
    PHONE_VERIFICATION("Verificação de telefone");

    private final String description;

    TokenType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
