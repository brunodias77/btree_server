package com.btree.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração do JWT (JSON Web Token).
 *
 * <p>Mapeia o segredo de assinatura e os tempos lógicos de expiração 
 * dos tokens de acesso e refresh a partir do {@code application.yml} (sob {@code security.jwt}).
 * Garante uma política mínima de segurança verificando se a chave de assinatura possui 32 chars+.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.jwt")
public class JwtConfig {

    private String secret;
    private long accessTokenExpirationMs = 900_000L;       // 15 min
    private long refreshTokenExpirationMs = 604_800_000L;  // 7 days

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "A variável de ambiente JWT_SECRET não está definida. " +
                            "Defina-a com no mínimo 32 caracteres antes de iniciar a aplicação.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ter no mínimo 32 caracteres para garantir a segurança do HS256.");
        }
    }
}
