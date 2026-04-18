package com.btree.infrastructure;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Classe base unificada para todos os testes de integração do módulo Infrastructure.
 *
 * <p>Qualquer classe de teste que estenda esta base terá automaticamente:
 * <ul>
 *   <li>O contexto do Spring Boot restrito a este módulo (sem levantar a API web real).</li>
 *   <li>Um container Docker PostgreSQL compartilhado e reutilizável via
 *       {@link PostgresTestContainer} (Singleton Pattern).</li>
 *   <li>O Flyway executado uma única vez na primeira inicialização do contexto,
 *       criando todos os schemas e tabelas necessários.</li>
 * </ul>
 *
 * <h3>Singleton Pattern — Por que não {@code @Testcontainers} + {@code @Container}?</h3>
 * <p>A abordagem padrão com {@code @Container static} em classe abstrata cria um container
 * <b>por classe de teste concreta</b>: 4 classes = 4 startups do PostgreSQL + 4 execuções
 * do Flyway. O Singleton inicia o container <b>uma única vez</b> para toda a suite,
 * reduzindo drasticamente o tempo de build.
 *
 * <h3>Isolamento entre testes</h3>
 * <p>Como o banco é compartilhado, cada classe de teste deve limpar seus dados no
 * {@code @BeforeEach} para garantir independência entre os cenários
 * (ex.: {@code repository.deleteAll()}).
 *
 * <h3>Perfil de teste</h3>
 * <p>O perfil {@code "test-integration"} deve desativar qualquer auto-configuração
 * que precise de serviços externos (MinIO, e-mail, etc.) para que o contexto
 * suba apenas com JPA + Flyway + Security.
 */
@ActiveProfiles("test-integration")
@SpringBootTest(classes = InfrastructureApplication.class)
@Tag("integrationTest")
public abstract class IntegrationTest {

    /**
     * Injeta as propriedades de conexão do container singleton no contexto do Spring.
     *
     * <p>O método é {@code static} pois o {@code @DynamicPropertySource} é processado
     * <b>antes</b> da criação do {@code ApplicationContext}. Referenciar o singleton
     * {@link PostgresTestContainer#INSTANCE} aqui garante que o container esteja
     * iniciado no momento em que as propriedades são lidas, pois o bloco estático de
     * {@link PostgresTestContainer} ja terá sido executado.
     *
     * <p>Tanto {@code spring.datasource.*} quanto {@code spring.flyway.*} são
     * sobrescritos porque o {@link com.btree.infrastructure.config.FlywayConfig}
     * injeta o {@code DataSource} manualmente — sem sobrescrever as propriedades do
     * Flyway, ele tentaria usar o banco de produção configurado no {@code application.yaml}.
     */
    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        final var pg = PostgresTestContainer.INSTANCE;

        // Datasource: pool de conexões HikariCP → JPA / Hibernate
        registry.add("spring.datasource.url",      pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);

        // Flyway: migrador de schema — usa credenciais idênticas ao datasource
        registry.add("spring.flyway.url",      pg::getJdbcUrl);
        registry.add("spring.flyway.user",     pg::getUsername);
        registry.add("spring.flyway.password", pg::getPassword);
    }
}
