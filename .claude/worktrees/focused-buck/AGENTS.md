# AGENTS.md

## Visao Geral

Este repositorio contem um e-commerce monolitico modular em Java com Spring Boot, organizado segundo Clean Architecture e Ports & Adapters.

O agregador Maven na raiz declara 5 modulos reais:

```text
modules/shared
modules/domain
modules/application
modules/infrastructure
modules/api
```

A direcao das dependencias e:

```text
shared  <-  domain  <-  application  <-  infrastructure
                              ^               |
                              |               |
                              +------ api ----+
```

## Regra Principal

`modules/shared`, `modules/domain` e `modules/application` nunca devem importar Spring.

Se uma classe precisa de `@Component`, `@Service`, `@Configuration`, `JpaRepository`, `RestController` ou qualquer tipo do ecossistema Spring, ela pertence a `modules/infrastructure` ou `modules/api`.

## O Que Vai Em Cada Modulo

| Modulo | Spring? | Responsabilidade |
|---|---|---|
| `modules/shared` | Nao | abstractions, value objects, enums, contracts, validation, pagination, use case base classes |
| `modules/domain` | Nao | entidades, aggregates, gateways, domain events, validators, erros de dominio |
| `modules/application` | Nao | use cases, commands, outputs, jobs, handlers de eventos de aplicacao |
| `modules/infrastructure` | Sim | JPA entities, repositories, gateways concretos, seguranca, configs Spring, Flyway |
| `modules/api` | Sim | controllers REST, DTOs HTTP, exception handlers, bootstrap, wiring de use cases |

## Estrutura Esperada

### Application

Use cases devem ficar em caminhos como:

```text
modules/application/src/main/java/com/btree/application/usecase/<contexto>/<feature>/
```

Padrao:

```text
<Verbo><Entidade>UseCase.java
<Verbo><Entidade>Command.java
<Verbo><Entidade>Output.java
```

Regras:
- `Command` e `Output` devem ser `record`.
- Use cases de escrita retornando dado estendem `UseCase<I, O>`.
- Use cases sem retorno usam `UnitUseCase<I>`.
- Queries usam `QueryUseCase<I, O>`.

### Domain

Entidades e aggregates pertencem ao dominio e nao devem conhecer detalhes de framework.

Regras:
- entidades de dominio usam factory methods como `create(...)`, `with(...)`, etc.
- evitar construtores publicos quando o modelo ja usa factories.
- toda mudanca relevante de aggregate deve registrar `DomainEvent` via `registerEvent()`.
- gateways do dominio sao interfaces, nunca implementacoes concretas.

### Infrastructure

Tudo que depende de banco, JWT, JPA, Spring Security, Flyway ou integracoes externas fica aqui.

Regras:
- JPA entity nao e entidade de dominio.
- conversao entre JPA e dominio deve acontecer na infraestrutura, tipicamente em gateway/adaptador.
- migrations Flyway ficam em:

```text
modules/infrastructure/src/main/resources/db/migration/
```

Padrao de nome:

```text
V<NNN>__<descricao>.sql
```

### API

Controllers apenas traduzem HTTP para a camada de aplicacao.

Regras:
- request DTO -> command
- invoca use case
- output -> response DTO
- nao colocar regra de negocio no controller
- o wiring dos beans de use case fica em `modules/api/src/main/java/com/btree/api/config/UseCaseConfig.java`

## Convencoes de Nomenclatura

- Use case: `<Verbo><Entidade>UseCase`
- Command: `<Verbo><Entidade>Command`
- Output: `<Entidade>Output` ou `<Verbo><Entidade>Output`
- Gateway de dominio: `<Entidade>Gateway`
- Implementacao JPA/gateway: `<Entidade>JpaGateway` ou nome equivalente aderente ao contexto
- JPA repository: `<Entidade>JpaRepository`
- JPA entity: `<Entidade>Entity`
- Controller: `<Contexto>Controller`
- Request/Response HTTP: `<Acao><Entidade>Request` e `<Entidade>Response`

## Banco de Dados

O projeto usa PostgreSQL com os schemas:

- `shared`
- `users`
- `catalog`
- `cart`
- `orders`
- `payments`
- `coupons`

Convencoes importantes:
- preferir UUID v7 nas chaves primarias
- respeitar soft delete com `deleted_at IS NULL` onde aplicavel
- respeitar optimistic locking nas entidades com coluna `version`
- enums de banco vivem no schema `shared`
- tabelas particionadas devem ser tratadas sem quebrar a estrategia de persistencia existente

## Contextos de Negocio

- Users
- Catalog
- Cart
- Orders
- Payments
- Coupons
- Shared

Ao implementar algo novo, descubra primeiro o contexto correto e mantenha o codigo dentro dele.

## Padrões Arquiteturais

- Clean Architecture
- Ports & Adapters
- Notification Pattern para validacao
- Outbox Pattern para eventos de dominio

Consequencias praticas:
- `application` depende de interfaces do `domain`, nunca de implementacoes
- `infrastructure` implementa gateways do `domain`
- `api` chama use cases, nao repositories
- validacao rica de negocio deve acontecer fora do controller

## Testes

- `modules/domain` e `modules/application`: testes unitarios com JUnit 5 e Mockito, sem Spring
- `modules/infrastructure`: testes de integracao (`*IT.java`) com Testcontainers/PostgreSQL
- `modules/api`: testes end-to-end subindo a aplicacao completa quando necessario

Ao criar funcionalidade nova, adicione ou atualize testes na camada apropriada.

## Regras de Trabalho Para Agentes

Antes de editar, confirme:
- qual modulo sera alterado
- qual contexto de negocio esta sendo afetado
- se a mudanca exige ou nao Spring
- se a classe deve ser record, entidade, gateway, config ou DTO

Ao gerar codigo:
- nao introduza Spring em `shared`, `domain` ou `application`
- nao exponha entidades de dominio diretamente na API
- nao mova regra de negocio para controller ou repository
- nao use `@Autowired` em use cases
- prefira injecao por construtor e wiring explicito em configuracao
- preserve a separacao entre entidade de dominio e entidade JPA

Ao alterar endpoints:
- considere o `context-path` configurado em `modules/api/src/main/resources/application.yaml`
- documentacao Swagger/OpenAPI deve refletir a rota real publicada

Ao alterar persistencia:
- respeite schema correto
- respeite soft delete e versionamento
- evite deletes fisicos em entidades soft-deletable

## Fonte Desta Convencao

Este `AGENTS.md` foi consolidado a partir do skill local em `.agents/skills/senior-spring-boot/SKILL.md` e ajustado para a estrutura real deste repositorio.
