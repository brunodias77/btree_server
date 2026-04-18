# Task: UC-07 — RequestPasswordReset

## 📋 Resumo
A funcionalidade `RequestPasswordReset` é responsável pelo primeiro passo do fluxo de "Esqueci minha senha". Quando o usuário informa seu email, o sistema gera um token seguro e temporário (com validade curta, ex: 15 ou 30 minutos), salva-o no banco de dados e dispara o evento para enviar o link de redefinição por e-mail. Para evitar ataques de enumeração, o sistema não deve revelar se o e-mail existe ou não.

## 🎯 Objetivo
Receber um endereço de email, localizar o usuário correspondente e, se existir e estiver ativo, gerar e salvar um novo `UserToken` do tipo `PASSWORD_RESET`. O endpoint deve sempre retornar sucesso (HTTP 200) independentemente da existência do e-mail, garantindo privacidade (anti-enumeration).

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `ALTA` (P1)
* **Endpoint:** `POST /v1/auth/password/forgot`
* **Tabelas do Banco:** `users.user_tokens`, `users.users`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/events/PasswordResetRequestedEvent.java` (Criar evento de domínio contendo o token cru para envio por email)
2. `domain/src/main/java/com/btree/domain/users/entities/User.java` (Adicionar método `requestPasswordReset(token, expiration)` que gera o evento de domínio)

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/password/forgot/IRequestPasswordResetUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/password/forgot/RequestPasswordResetUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/password/forgot/RequestPasswordResetInput.java`

### `infrastructure`
1. *(O `UserTokenPostgresGateway` e o Repositório correspondente já foram criados no UC-06, portanto não precisam de novas classes. Apenas garantir que suportem o Enum ou String de "PASSWORD_RESET")*

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar rota)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Adicionar o `@Bean` do caso de uso)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* A entidade `UserToken` já existe. Usaremos o Factory Method `create` passando o tipo `"PASSWORD_RESET"`.
* Na entidade `User`, criaremos um método `requestPasswordReset(String rawToken, Instant expiresAt)`. Esse método **não altera** o estado da entidade, mas sim registra o `PasswordResetRequestedEvent` (que deve herdar de `DomainEvent`) na lista interna de eventos para que o *EventPublisher* seja acionado e o e-mail possa ser despachado assincronamente por outro módulo.

### 2. Contrato de Entrada/Saída (Application)
* **`RequestPasswordResetInput` (Record):** `email` (String).
* **Saída:** Retornar `Either<Notification, Void>`.

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar se o `email` de entrada é válido e não é nulo/vazio. Se falhar, adicionar falha via `notification.append()` e retornar `Left(notification)`.
3. Buscar o usuário pelo e-mail no gateway: `userGateway.findByEmail(input.email())`.
4. Se o usuário **não existir**, **NÃO** retornar erro. Por segurança, devemos agir de forma silenciosa e retornar `Right(null)` para o atacante não descobrir quais e-mails estão cadastrados na base.
5. Se o usuário existir, mas estiver inativo (`!user.isEnabled()`), também retornar `Right(null)` silenciosamente.
6. Gerar um token randômico seguro (UUID longo, SecureRandom ou TokenProvider com JWT de curta duração).
7. Hashear o token recém gerado.
8. Criar a instância de `UserToken` apontando para o ID do usuário, tipo `"PASSWORD_RESET"` e com data de expiração (ex: `Instant.now().plus(Duration.ofMinutes(30))`).
9. Acionar `user.requestPasswordReset(rawToken, expiresAt)` para empilhar o evento de domínio.
10. Executar a persistência via `transactionManager.execute(() -> { ... })`:
    * Salvar o token no banco: `userTokenGateway.create(userToken)`.
    * Chamar o update do usuário (`userGateway.update(user)`) **exclusivamente para disparar os Domain Events** pendentes através da infraestrutura de eventos.
11. Retornar `Right(null)`.

### 4. Persistência (Infrastructure)
* A infraestrutura de `UserToken` já está operante pelo UC-06.
* Garantir que o `update(user)` na camada Postgres publique todos os eventos de domínio (`domainEventPublisher.publishAll(user.getDomainEvents())`).

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` do `requestPasswordResetUseCase` no `UsersModuleConfig`.
* Adicionar a rota `POST /v1/auth/password/forgot` no `AuthController`.
* Desempacotar o `Either`. Se falhar por validação (`Left`), mapear para `422 Unprocessable Entity`.
* Em caso de sucesso, retornar `200 OK` e um JSON via `ApiResponse.success(null)`.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `UserError.EMAIL_EMPTY` / `EMAIL_INVALID_FORMAT` | O email enviado não é válido | `422 Unprocessable Entity` |

---

## 🌐 Contrato da API REST

### Request
```json
{
  "email": "bruno@admin.com"
}
```

### Response (Sucesso)
```json
{
  "data": null,
  "success": true,
  "timestamp": "2026-04-06T12:00:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. Evento de Domínio `PasswordResetRequestedEvent` e modificação em `User`.
2. Contratos: Input e `IRequestPasswordResetUseCase`.
3. Implementação do `RequestPasswordResetUseCase` garantindo idempotência e segurança (anti-enumeration).
4. `@Bean` configuration.
5. Endpoint `POST /v1/auth/password/forgot` na API.
6. Testes unitários e de integração.