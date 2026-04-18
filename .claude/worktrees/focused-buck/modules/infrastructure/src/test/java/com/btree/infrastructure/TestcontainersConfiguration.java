package com.btree.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Configuração de Testcontainers para o smoke test {@link InfrastructureApplicationTests}.
 *
 * <p>Utiliza {@link PostgresTestContainer#INSTANCE} — o mesmo container singleton
 * compartilhado pela suite de integração — para garantir que o contexto de smoke
 * test não suba um terceiro container Docker separado.
 *
 * <p>A anotação {@code @ServiceConnection} instrui o Spring Boot a configurar
 * automaticamente as propriedades {@code spring.datasource.*} a partir das
 * coordenadas do container retornado, sem precisar de {@code @DynamicPropertySource}.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /**
     * Expõe o container singleton como bean Spring para que o mecanismo
     * {@code @ServiceConnection} consiga ler a JDBC URL, usuário e senha
     * e injetá-los no {@code DataSource} do contexto de teste.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        // Retorna a instância já iniciada — evita criar um segundo container.
        return PostgresTestContainer.INSTANCE;
    }
}
