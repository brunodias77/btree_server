package com.btree.infrastructure;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Container PostgreSQL singleton compartilhado por toda a suite de testes de integração.
 *
 * <p>O padrão Singleton de Testcontainers garante que apenas <b>um único container Docker</b>
 * seja iniciado para toda a execução do {@code mvn test}, independentemente de quantas
 * classes de teste estendam {@link IntegrationTest}. O container é iniciado no
 * carregamento estático da classe e encerrado automaticamente pelo Testcontainers via
 * {@code Ryuk} (processo de limpeza) ao fim da JVM.
 *
 * <h3>Motivação</h3>
 * <p>Sem o Singleton, o ciclo {@code @Container static} em uma classe abstrata faz com que
 * o Testcontainers suba/derrube um container por <b>classe de teste concreta</b>. Com
 * quatro classes de integração, isso significa quatro ciclos de pull + startup + migrate
 * do Flyway — desnecessariamente custoso.
 *
 * <h3>Isolamento entre testes</h3>
 * <p>O banco é compartilhado, portanto cada classe de teste é responsável por limpar
 * seus próprios dados no {@code @BeforeEach} (ex.: {@code repository.deleteAll()})
 * para garantir independência entre os cenários.
 *
 * <h3>Ryuk e encerramento seguro</h3>
 * <p>O método {@code start()} é chamado explicitamente no bloco estático. O Testcontainers
 * registra automaticamente um shutdown hook via Ryuk que encerra o container ao fim da JVM,
 * então <b>não é necessário</b> chamar {@code stop()} manualmente.
 */
public final class PostgresTestContainer {

    /** Versão do PostgreSQL usada nos testes — deve ser igual ou próxima à de produção. */
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";

    /**
     * Instância singleton do container PostgreSQL.
     *
     * <p>O campo é {@code static final} e inicializado em bloco estático: o container é
     * criado e iniciado uma única vez quando a classe é carregada pela JVM, e reutilizado
     * por todos os testes subsequentes.
     */
    public static final PostgreSQLContainer<?> INSTANCE;

    static {
        // Cria e inicia o container uma única vez para toda a suite de testes.
        INSTANCE = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName("btree_test")
                .withUsername("test_user")
                .withPassword("test_pass")
                // Reutilizar o container entre diferentes execuções de teste (desenvolvimento local).
                // Em CI, o Ryuk sempre inicia um container limpo, portanto esta flag é segura.
                .withReuse(true);

        INSTANCE.start();
    }

    /** Classe utilitária — não deve ser instanciada. */
    private PostgresTestContainer() {}
}
