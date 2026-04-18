package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public class UserError {
    public static final Error USER_NOT_FOUND = new Error("Usuário não encontrado");
    public static final Error USERNAME_ALREADY_EXISTS = new Error("Nome de usuário já está em uso");
    public static final Error EMAIL_ALREADY_EXISTS = new Error("E-mail já está em uso");

    // Validator errors
    public static final Error USERNAME_EMPTY = new Error("'username' não pode estar vazio");
    public static final Error USERNAME_TOO_LONG = new Error("'username' deve ter entre 3 e 50 caracteres");
    public static final Error USERNAME_INVALID_CHARS = new Error("'username' contém caracteres inválidos");

    public static final Error EMAIL_EMPTY = new Error("'email' não pode estar vazio");
    public static final Error EMAIL_TOO_LONG = new Error("'email' deve ter no máximo 255 caracteres");
    public static final Error EMAIL_INVALID_FORMAT = new Error("Formato de e-mail inválido");

    public static final Error PASSWORD_HASH_EMPTY = new Error("Hash de senha não pode estar vazio");

    public static final Error PASSWORD_EMPTY = new Error("'password' não pode estar vazio");
    public static final Error PASSWORD_TOO_SHORT = new Error("'password' deve ter no mínimo 8 caracteres");
    public static final Error PASSWORD_WEAK = new Error("'password' deve conter pelo menos uma letra maiúscula, uma minúscula, um número e um caractere especial");

    public static final Error TWO_FACTOR_ALREADY_ENABLED = new Error("Autenticação de dois fatores já está habilitada para este usuário");
    public static final Error INVALID_TOTP_CODE = new Error("Código TOTP inválido");

    public static final Error INVALID_CREDENTIALS = new Error("Credenciais inválidas");
    public static final Error ACCOUNT_DISABLED = new Error("Conta desativada");
    public static final Error ACCOUNT_LOCKED = new Error("Conta bloqueada");

    public static final Error SESSION_NOT_FOUND = new Error("Sessão inválida ou não encontrada");
    public static final Error SESSION_REVOKED = new Error("Sessão revogada");
    public static final Error SESSION_EXPIRED = new Error("Sessão expirada");

    public static final Error TOKEN_NOT_FOUND = new Error("Token inválido ou não encontrado");
    public static final Error TOKEN_EXPIRED = new Error("Token expirado");
    public static final Error TOKEN_ALREADY_USED = new Error("Token já utilizado");
    public static final Error TOKEN_INVALID_TYPE = new Error("Tipo de token inválido para esta operação");
    public static final Error EMAIL_ALREADY_VERIFIED = new Error("E-mail já verificado");

    private UserError() {}
}