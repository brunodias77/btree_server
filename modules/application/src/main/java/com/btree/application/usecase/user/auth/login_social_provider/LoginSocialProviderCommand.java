package com.btree.application.usecase.user.auth.login_social_provider;


/**
 * Input do caso de uso UC-09 — LoginWithSocialProvider.
 *
 * @param provider   identificador do provedor (ex: "google", "facebook")
 * @param token      ID token ou access token emitido pelo provedor
 * @param ipAddress  IP da requisição (para registro de sessão)
 * @param userAgent  User-Agent da requisição (para registro de sessão)
 */
public record LoginSocialProviderCommand(
        String provider,
        String token,
        String ipAddress,
        String userAgent
) { }
