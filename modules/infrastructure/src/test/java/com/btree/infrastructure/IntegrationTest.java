package com.btree.infrastructure;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base unificada para todos os Testes de Integração do módulo Infrastructure.
 *
 * <p>
 * Qualquer classe de teste que estenda esta base subirá automaticamente:
 * <ul>
 * <li>O Contexto do Spring Boot restrito a este módulo (sem precisar levantar a
 * API web real).</li>
 * <li>Um Container Docker do PostgreSQL temporário e volátil via
 * Testcontainers.</li>
 * <li>O Flyway para criar e versionar todo o banco automaticamente dentro desse
 * container.</li>
 * </ul>
 */
@ActiveProfiles("test-integration")
@SpringBootTest(classes = InfrastructureApplication.class)
@Testcontainers
@Tag("integrationTest")
public abstract class IntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("btree_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redireciona tudo que dependa do banco de dados (Spring JPA e Flyway) para
        // acessar especificamente as credenciais da URL do Testcontainer recém criado.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }
}
