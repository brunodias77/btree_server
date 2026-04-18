Aqui está a lista atualizada com a inclusão do **Testcontainers**, organizada de forma consistente com o restante do documento:

---

### 1. Framework Principal (Spring Boot)

O projeto utiliza o ecossistema Spring Boot (com o `spring-boot-starter-parent` como base). As bibliotecas associadas são:

* **`spring-boot-starter-webmvc`**: Utilizado para a criação da API REST.
* **`spring-boot-starter-data-jpa`**: Para persistência de dados, mapeamento objeto-relacional (ORM) e integração com o Hibernate/JPA.
* **`spring-boot-starter-security`**: Para fornecer os mecanismos de autenticação e autorização.
* **`spring-boot-starter-validation`**: Para validação de dados nas requisições (Bean Validation).
* **`spring-boot-starter-actuator`**: Para fornecer *endpoints* de monitorização e métricas da aplicação.
* **`spring-boot-devtools`**: Ferramenta de apoio ao desenvolvimento, que permite *restarts* rápidos.

---

### 2. Base de Dados e Migrações

* **PostgreSQL (`org.postgresql:postgresql`)**: O *driver* principal para conexão com bases de dados PostgreSQL.
* **Flyway (`spring-boot-starter-flyway` e `org.flywaydb:flyway-database-postgresql`)**: Ferramenta de controlo de versões para a base de dados, encarregue de aplicar e gerir migrações SQL.

---

### 3. Segurança e Autenticação

* **JJWT (Java JWT)**: Utilizado para a geração e validação de *JSON Web Tokens* (JWT) para as sessões da API. Inclui as bibliotecas `jjwt-api`, `jjwt-impl` e `jjwt-jackson` (na versão 0.12.6).
* **TOTP (`dev.samstevens.totp:totp`)**: Biblioteca (na versão 1.7.1) utilizada para gerar e verificar senhas temporárias de uso único, implementando a autenticação de dois fatores (2FA).

---

### 4. Utilitários e Outros

* **Lombok (`org.projectlombok:lombok`)**: Utilizado para reduzir a verbosidade do código Java, gerando automaticamente código como *getters*, *setters* e construtores.
* **Vavr (`io.vavr:vavr`)**: Biblioteca (versão 0.10.4) focada em programação funcional para Java, fornecendo estruturas de dados imutáveis e mecanismos avançados de controlo de fluxo.
* **Springdoc OpenAPI (`org.springdoc:springdoc-openapi-starter-webmvc-ui`)**: Ferramenta (versão 2.8.6) para documentar automaticamente a API e expor uma interface interativa (Swagger UI).

---

### 5. Bibliotecas de Testes

O projeto inclui diversas bibliotecas para garantir a qualidade do código, sendo o ecossistema de testes suportado pelas seguintes dependências:

* **JUnit Jupiter (JUnit 5)**: O framework principal de testes em Java, utilizado para a escrita e execução dos casos de teste (fornecido transitivamente pelos *starters* de teste do Spring Boot).
* **Mockito**: A principal biblioteca de *mocking* em Java, utilizada para criar objetos falsos (*mocks*) e isolar os testes unitários (fornecida transitivamente pelos *starters* de teste do Spring Boot).

#### 🔹 Testes de Integração com Containers

* **Testcontainers (`org.testcontainers:testcontainers`)**: Biblioteca que permite executar testes de integração utilizando containers Docker reais, garantindo um ambiente próximo ao de produção.
* **Testcontainers JUnit (`org.testcontainers:junit-jupiter`)**: Integração com o JUnit 5 para gerir o ciclo de vida dos containers durante os testes.
* **Testcontainers PostgreSQL (`org.testcontainers:postgresql`)**: Módulo específico para subir instâncias de PostgreSQL em containers para testes de persistência.

#### 🔹 Starters de Teste do Spring Boot

* **`spring-boot-starter-webmvc-test`**: Para testar a camada *web* (Controladores REST).
* **`spring-boot-starter-data-jpa-test`**: Para testes focados na camada de persistência.
* **`spring-boot-starter-security-test`**: Para testes com contextos de segurança e utilizadores autenticados.
* **`spring-boot-starter-validation-test`**: Para testar as regras de validação de dados.
* **`spring-boot-starter-actuator-test`**: Para testar os *endpoints* de métricas.
* **`spring-boot-starter-flyway-test`**: Para testar as migrações da base de dados.

---


