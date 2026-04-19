package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public class AuthError {
    public static final Error INVALID_CREDENTIALS = new Error("Credenciais inválidas");
    public static final Error INVALID_REFRESH_TOKEN = new Error("Refresh token inválido ou expirado");
    public static final Error INVALID_SOCIAL_TOKEN = new Error("Token social inválido ou expirado");
    public static final Error UNSUPPORTED_PROVIDER = new Error("Provedor social não suportado");
    public static final Error INVALID_TOTP_CODE = new Error("Código TOTP inválido ou expirado");
    public static final Error ACCOUNT_LOCKED = new Error("Conta temporariamente bloqueada devido a múltiplas tentativas de login malsucedidas");
    public static final Error ACCOUNT_DISABLED = new Error("Conta desativada");

    private AuthError() {}
}