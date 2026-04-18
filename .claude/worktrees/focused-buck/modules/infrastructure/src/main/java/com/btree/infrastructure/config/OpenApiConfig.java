package com.btree.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do framework de documentação interativa Open API 3.0 (Swagger).
 *
 * <p>Determina como os módulos exporão suas rotas automaticamente para o front-end 
 * ou clientes via {@code /swagger-ui.html}.
 * Adiciona ao contexto global da documentação as especificações do Bearer Token HTTP (JWT), 
 * o que habilita o botão 'Authorize' em todas as rotas restritas da API.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "Bearer";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BTree E-commerce API")
                        .version("1.0.0")
                        .description("Plataforma de e-commerce B2C — mercado brasileiro"))
                .addServersItem(new Server()
                        .url("/api")
                        .description("Base path da API"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
