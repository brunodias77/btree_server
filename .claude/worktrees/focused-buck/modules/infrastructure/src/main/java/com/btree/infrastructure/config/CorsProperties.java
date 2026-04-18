package com.btree.infrastructure.config;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de CORS configuráveis via {@code application.yml}.
 *
 * <p>Exemplo de configuração para desenvolvimento:
 * <pre>
 * cors:
 *   allowed-origins:
 *     - http://localhost:3000
 *     - http://localhost:5173
 *   max-age: 3600
 * </pre>
 *
 * <p>Em produção, nunca use {@code *} com {@code allow-credentials: true}.
 * Liste explicitamente os domínios front-end permitidos.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    private String[] allowedOrigins = {};
    private long maxAge = 3600;
}
