package com.btree.infrastructure.security.service;

import com.btree.domain.user.gateway.TotpGateway;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Component;

/**
 * Implementação de {@link TotpGateway} usando a biblioteca
 * {@code dev.samstevens.totp} (TOTP/RFC 6238).
 *
 * <p>Configuração adotada (compatível com Google Authenticator e afins):
 * <ul>
 *   <li>Algoritmo: SHA1</li>
 *   <li>Dígitos: 6</li>
 *   <li>Período: 30 segundos</li>
 *   <li>Janela de tolerância: ±1 período (90 s total)</li>
 * </ul>
 */
@Component
public class TotpService implements TotpGateway {

    private static final int DIGITS = 6;
    private static final int PERIOD = 30;
    private static final HashingAlgorithm ALGORITHM = HashingAlgorithm.SHA1;

    private final SecretGenerator secretGenerator;
    private final CodeVerifier codeVerifier;

    public TotpService() {
        this.secretGenerator = new DefaultSecretGenerator(32);

        final TimeProvider timeProvider = new SystemTimeProvider();
        final CodeGenerator codeGenerator = new DefaultCodeGenerator(ALGORITHM, DIGITS);
        final DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setAllowedTimePeriodDiscrepancy(1); // tolera ±1 período (clock drift)
        this.codeVerifier = verifier;
    }

    /**
     * Gera um novo segredo Base32 aleatório de 32 caracteres.
     * Deve ser armazenado criptografado no banco (coluna {@code two_factor_secret}).
     */
    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Retorna o URI {@code otpauth://totp/...} para geração do QR Code.
     *
     * <p>O URI segue o formato definido pela Google Key URI Format:
     * {@code otpauth://totp/<issuer>:<accountName>?secret=<secret>&issuer=<issuer>&...}
     *
     * <p>Esse URI pode ser codificado em QR Code e lido por qualquer
     * aplicativo autenticador (Google Authenticator, Authy, etc.).
     *
     * @param secret      segredo Base32 gerado por {@link #generateSecret()}
     * @param accountName identificador visível ao usuário (ex: e-mail)
     * @param issuer      nome da aplicação exibido no autenticador (ex: "Btree")
     */
    @Override
    public String getUriForImage(final String secret, final String accountName, final String issuer) {
        final QrData data = new QrData.Builder()
                .label(accountName)
                .secret(secret)
                .issuer(issuer)
                .algorithm(ALGORITHM)
                .digits(DIGITS)
                .period(PERIOD)
                .build();
        return data.getUri();
    }

    /**
     * Valida o código TOTP fornecido pelo usuário contra o segredo armazenado.
     *
     * @param secret segredo Base32 do usuário
     * @param code   código de 6 dígitos informado
     * @return {@code true} se o código for válido dentro da janela de tolerância
     */
    @Override
    public boolean isValidCode(final String secret, final String code) {
        return codeVerifier.isValidCode(secret, code);
    }
}
