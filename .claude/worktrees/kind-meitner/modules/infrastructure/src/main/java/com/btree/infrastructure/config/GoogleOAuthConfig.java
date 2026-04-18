package com.btree.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração para autenticação social via Google (OAuth2).
 *
 * <p>Mapeia as credenciais configuradas com o prefixo {@code security.google} no {@code application.yml}.
 * Valida a existência do Client ID durante a inicialização para não rodar a aplicação quebrando.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security.google")
public class GoogleOAuthConfig {

    private String clientId;

    @PostConstruct
    public void validate() {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "A variável de ambiente GOOGLE_CLIENT_ID não está definida. " +
                            "Defina-a com o Client ID OAuth2 do Google antes de iniciar a aplicação.");
        }
    }
}