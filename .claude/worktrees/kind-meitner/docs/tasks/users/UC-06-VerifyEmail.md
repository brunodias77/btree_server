# Task: UC-06 — VerifyEmail

## 📋 Resumo
Esta funcionalidade permite confirmar o endereço de email de um usuário através de um token único enviado para sua caixa de entrada no momento do cadastro. A verificação de email é essencial para garantir a autenticidade das contas e liberar funcionalidades restritas do sistema.

## 🎯 Objetivo
Validar o token de confirmação recebido. Se o token for válido, pertencer ao tipo correto (verificação de email), não estiver expirado ou já utilizado, o sistema deve marcar o email do usuário associado como verificado (`email_verified = true`) e marcar o token como consumido/invalido, garantindo a idempotência e segurança do processo.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `ALTA` (P1)
* **Endpoint:** `POST /v1/auth/email/verify`
* **Tabelas do Banco:** `users.user_tokens`, `users.users`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/entities/UserToken.java` (Criar a entidade para representar o token no banco)
2. `domain/src/main/java/com/btree/domain/users/gateways/IUserTokenGateway.java` (Interface do repositório de tokens)
3. `domain/src/main/java/com/btree/domain/users/errors/UserTokenError.java` (Mapear erros relacionados ao token)
4. `domain/src/main/java/com/btree/domain/users/entities/User.java` (Adicionar método `verifyEmail()`)

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/verifyemail/IVerifyEmailUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/verifyemail/VerifyEmailUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/verifyemail/VerifyEmailInput.java`

### `infrastructure`
1. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/entities/UserTokenJpaEntity.java`
2. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/repositories/UserTokenRepository.java`
3. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/gateways/UserTokenPostgresGateway.java`

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar rota)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Adicionar o `@Bean` do caso de uso)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* **`UserToken` (Entity):** Possui `id`, `userId`, `token` (hash ou raw dependendo da segurança), `type` (ex: `EMAIL_VERIFICATION`, `PASSWORD_RESET`), `expiresAt`, `usedAt`.
* **Métodos na Entidade:** `isExpired()`, `isUsed()`, `markAsUsed()`.
* **Erros de Domínio:** `UserTokenError.INVALID_TOKEN`, `UserTokenError.TOKEN_EXPIRED`, `UserTokenError.TOKEN_ALREADY_USED`.
* Na entidade `User`, adicionar o comportamento `verifyEmail()` que define a propriedade interna `emailVerified` como `true`.

### 2. Contrato de Entrada/Saída (Application)
* **`VerifyEmailInput` (Record):** `token` (String).
* **Saída:** Optaremos por retornar `Either<Notification, Void>`, já que o sucesso do endpoint (status 200) será suficiente como output.

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar se o `token` de entrada não é nulo ou vazio. Adicionar erro `INVALID_TOKEN` se for.
3. Buscar o `UserToken` no `userTokenGateway` através da string do token.
4. Se o token não existir, retornar `Left(UserTokenError.INVALID_TOKEN)`.
5. Se o tipo de token não for de verificação de email, retornar `Left(UserTokenError.INVALID_TOKEN)`.
6. Se o token estiver expirado (`isExpired()`), retornar `Left(UserTokenError.TOKEN_EXPIRED)`.
7. Se o token já tiver sido usado (`isUsed()`), retornar `Left(UserTokenError.TOKEN_ALREADY_USED)`.
8. Buscar o `User` associado pelo `userId` contido no token. Se não encontrar, retornar `Left(UserError.USER_NOT_FOUND)`.
9. Modificar os estados (Mutações):
   * Executar `user.verifyEmail()`.
   * Executar `userToken.markAsUsed()`.
10. Executar persistência atômica via `transactionManager.execute(() -> { ... })`:
    * `userGateway.update(user)`
    * `userTokenGateway.update(userToken)`
11. Retornar `Right(null)`.

### 4. Persistência (Infrastructure)
* Criar a tabela `users.user_tokens` via Flyway (caso não exista, anotar como passo 1).
* Mapear `UserTokenJpaEntity` vinculando `@Table(name = "user_tokens", schema = "users")`.
* Métodos essenciais no gateway: `findByToken(String token)`, `update(UserToken userToken)`, `create(UserToken userToken)`.
* Conversões `toAggregate()` e `from(UserToken)` manuais e limpas.

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` do `verifyEmailUseCase` no `UsersModuleConfig`.
* Adicionar a rota `POST /v1/auth/email/verify` no `AuthController`.
* Desempacotar o `Either`. Se falhar (`Left`), mapear os erros específicos para os respectivos Status Code (ex: `400 Bad Request` ou `422 Unprocessable Entity`).
* Em caso de sucesso, retornar `200 OK` e um JSON via `ApiResponse.success(null)`.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `UserTokenError.INVALID_TOKEN` | O token não foi encontrado, é vazio ou não é do tipo email | `400 Bad Request` |
| `UserTokenError.TOKEN_EXPIRED` | O token passou da data de expiração | `400 Bad Request` |
| `UserTokenError.TOKEN_ALREADY_USED` | O token já foi marcado como consumido anteriormente | `400 Bad Request` |
| `UserError.USER_NOT_FOUND` | O usuário dono do token foi deletado | `404 Not Found` |

---

## 🌐 Contrato da API REST

### Request
```json
{
  "token": "d7b9c2a3-f5e6-48b9-9f7a-8d1e2c3b4a5f"
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
1. Migration do banco de dados para a tabela `users.user_tokens` (se ainda não existir).
2. Entidade de Domínio `UserToken` e `UserTokenError`.
3. Adicionar `verifyEmail()` na entidade `User`.
4. Contratos: Input e `IVerifyEmailUseCase`.
5. Interface do Gateway `IUserTokenGateway` no Domain.
6. Implementação do `VerifyEmailUseCase`.
7. `UserTokenJpaEntity`, `UserTokenRepository` e Implementação do `UserTokenPostgresGateway`.
8. `@Bean` configuration.
9. `Controller` na API.
10. Testes unitários e de integração.