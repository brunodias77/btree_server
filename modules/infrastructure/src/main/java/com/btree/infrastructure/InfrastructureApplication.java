package com.btree.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Configuração base do módulo {@code infrastructure} para testes de integração.
 *
 * <p><b>Não é o entrypoint de produção.</b> O ponto de entrada real da aplicação
 * é {@code com.btree.api.ApiApplication} no módulo {@code api}.
 *
 * <p>Esta classe existe para permitir que os testes de integração do módulo
 * {@code infrastructure} subam o contexto Spring via {@code @SpringBootTest}
 * sem depender do módulo {@code api}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.btree.infrastructure")
public class InfrastructureApplication {

	public static void main(String[] args) {
		SpringApplication.run(InfrastructureApplication.class, args);
	}

}
