package com.btree.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuração central do Spring Data JPA.
 *
 * <p>Habilita suporte a:
 * <ul>
 *     <li><b>Auditing:</b> Preenchimento automático de campos do tipo @CreatedDate e @LastModifiedDate.</li>
 *     <li><b>Transaction Management:</b> Habilita suporte declarativo de transações (@Transactional).</li>
 *     <li><b>Repositories:</b> Escaneia interfaces de repositórios JPA dentro do contexto infraestrutural.</li>
 * </ul>
 */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.btree.infrastructure")
public class JpaConfig {
}
