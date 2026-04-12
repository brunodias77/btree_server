package com.btree.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de criptografia para dados sensíveis em repouso.
 *
 * <p>Exemplo de configuração:
 * <pre>
 * security:
 *   encryption:
 *     secret: "minha-chave-secreta-de-32-chars!!"   # mín. 32 chars (AES-256)
 *     salt: "deadbeefcafebabe"                        # 16 chars hex (8 bytes)
 * </pre>
 *
 * <p>Usado atualmente para criptografar {@code users.users.two_factor_secret}
 * via {@link com.btree.infrastructure.shared.persistence.EncryptedStringConverter}.
 */

@Data
@Configuration
@ConfigurationProperties(prefix = "security.encryption")
public class EncryptionConfig {

    /** Senha para derivação da chave AES-256 (mínimo 32 caracteres). */
    private String secret;

    /** Salt hex-encoded (16 caracteres = 8 bytes) para o KDF. */
    private String salt;

}
