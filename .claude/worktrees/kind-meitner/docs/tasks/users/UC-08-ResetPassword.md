# Task: UC-08 — ResetPassword

## 📋 Resumo
A funcionalidade `ResetPassword` é a segunda e última etapa do fluxo de "Esqueci minha senha". O usuário clica no link recebido por e-mail, acessa uma página no front-end e envia sua nova senha acompanhada do token temporário. A API valida a autenticidade e validade do token, atualiza a senha do usuário e revoga (ou consome) o token, evitando reuso.

## 🎯 Objetivo
Receber um token de redefinição e uma nova senha. Validar se o token existe, é do tipo `PASSWORD_RESET`, não está expirado e não foi utilizado. Se tudo estiver correto, efetuar o hash da nova senha, atualizar a entidade `User`, marcar o `UserToken` como utilizado e persistir as alterações de forma transacional.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `ALTA` (P1)
* **Endpoint:** `POST /v1/auth/password/reset`
* **Tabelas do Banco:** `users.user_tokens`, `users.users`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/entities/User.java` (Criar/verificar se existe método `changePassword(String newPasswordHash)`)

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/password/reset/IResetPasswordUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/password/reset/ResetPasswordUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/password/reset/ResetPasswordInput.java`

### `infrastructure`
1. *(A persistência para `User` e `UserToken` já está completa e funcional pelos casos de uso anteriores).*

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar rota)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Adicionar o `@Bean` do caso de uso)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* Na entidade `User`, adicionar ou utilizar um método `changePassword(String newPasswordHash)`. O método deve atualizar o campo `passwordHash` e possivelmente atualizar `updatedAt`.

### 2. Contrato de Entrada/Saída (Application)
* **`ResetPasswordInput` (Record):** `token` (String), `newPassword` (String).
* **Saída:** Retornar `Either<Notification, Void>`.

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar as entradas:
   * Se o `token` for nulo ou vazio, append `UserTokenError.INVALID_TOKEN`.
   * Se a `newPassword` for nula, vazia ou não atender aos requisitos de força (min. 8 chars, 1 maiúscula, 1 minúscula, 1 número, 1 símbolo), append `UserError.PASSWORD_WEAK`.
   * Se houver erros, retornar `Left(notification)`.
3. Processar o token de entrada: obter o seu hash correspondente usando o `TokenProvider`.
4. Buscar o `UserToken` no `userTokenGateway` através do `tokenHash`.
5. Se o token não existir ou não for do tipo `"PASSWORD_RESET"`, retornar `Left(UserTokenError.INVALID_TOKEN)`.
6. Se o token estiver expirado (`isExpired()`), retornar `Left(UserTokenError.TOKEN_EXPIRED)`.
7. Se o token já tiver sido usado (`isUsed()`), retornar `Left(UserTokenError.TOKEN_ALREADY_USED)`.
8. Buscar o `User` dono do token pelo `userId` no `userGateway`.
9. Se o usuário não existir (removido no meio tempo), retornar `Left(UserError.USER_NOT_FOUND)`.
10. Se o usuário estiver inativo (`!user.isEnabled()`), retornar `Left(UserError.USER_NOT_FOUND)` (ou um erro de permissão adequado).
11. Executar as mutações nas entidades:
    * Fazer o hash seguro da nova senha via `TokenProvider` (ex: `String hashedPwd = tokenProvider.hashPassword(input.newPassword())`).
    * Atualizar a senha do usuário: `user.changePassword(hashedPwd)`.
    * Marcar o token como consumido: `userToken.markAsUsed()`.
12. Executar a persistência atômica via `transactionManager.execute(() -> { ... })`:
    * Atualizar o usuário: `userGateway.update(user)`.
    * Atualizar o token: `userTokenGateway.update(userToken)`.
13. Retornar `Right(null)`.

### 4. Persistência (Infrastructure)
* A persistência de `User` e `UserToken` já está completa. Não há alterações a serem feitas.

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` do `resetPasswordUseCase` no `UsersModuleConfig`.
* Adicionar a rota `POST /v1/auth/password/reset` no `AuthController`.
* Desempacotar o `Either`. Se falhar por validação (`Left`), mapear para `400 Bad Request` ou `422 Unprocessable Entity` (para senha fraca).
* Em caso de sucesso, retornar `200 OK` e um JSON via `ApiResponse.success(null)`.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `UserTokenError.INVALID_TOKEN` | Token nulo, vazio, inexistente ou tipo errado | `400 Bad Request` |
| `UserTokenError.TOKEN_EXPIRED` | O token ultrapassou o limite de tempo | `400 Bad Request` |
| `UserTokenError.TOKEN_ALREADY_USED` | O token já foi utilizado anteriormente | `400 Bad Request` |
| `UserError.PASSWORD_WEAK` | A nova senha não cumpre os requisitos mínimos | `422 Unprocessable Entity` |

---

## 🌐 Contrato da API REST

### Request
```json
{
  "token": "d7b9c2a3-f5e6-48b9-9f7a-8d1e2c3b4a5f",
  "new_password": "NewStrongPassword@123"
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
1. Modificar entidade `User` (adicionar `changePassword`).
2. Contratos: Input e `IResetPasswordUseCase`.
3. Implementação do `ResetPasswordUseCase`.
4. `@Bean` configuration.
5. Endpoint `POST /v1/auth/password/reset` na API.
6. Testes unitários e de integração.