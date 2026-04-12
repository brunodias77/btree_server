package com.btree.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração Global da camada Web / MVC do Spring.
 *
 * <p>Responsável atualmente por registrar em toda a API a política de CORS 
 * (Cross-Origin Resource Sharing), bloqueando ou permitindo o consumo de recursos 
 * por origens diferentes baseadas nas configurações do {@code application.yml} 
 * (injetadas via {@link CorsProperties}).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebMvcConfig(final CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getAllowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With")
                .allowCredentials(true)
                .maxAge(corsProperties.getMaxAge());
    }
}

