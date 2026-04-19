package com.btree.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Classe responsável por configurar manualmente a execução e o escopo do Flyway 
 * (ferramenta de versionamento e migração de banco de dados).
 * 
 * Centraliza a configuração das migrações do Monólito, garantindo que as tabelas
 * de todos os sub-módulos sejam criadas corretamente, divididas por schemas do PostgreSQL.
 */
@Configuration
public class FlywayConfig {

    /**
     * Define o bean do Flyway e obriga executar o "migrate" no momento da inicialização da aplicação (initMethod).
     * @param dataSource A fonte de conexão com o banco (injetada automaticamente pelo Spring Boot).
     * @return A instância configurada do Flyway.
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(final DataSource dataSource) {
        return Flyway.configure()
                // Atrela o versionador ao PostgreSQL configurado na infra
                .dataSource(dataSource)
                
                // Mapeia TODOS os schemas lógicos/físicos que o Flyway deve observar e criar (se não existirem).
                // Isso reflete a arquitetura do Monólito Modular orientada ao banco:
                .schemas("shared", "users", "catalog", "cart", "orders", "payments", "coupons")
                
                // Local onde os arquivos SQL (ex: V1__criar_tabelas.sql) estarão armazenados
                .locations("classpath:db/migration")
                
                // Consolida e carrega as configurações
                .load();
    }

}
