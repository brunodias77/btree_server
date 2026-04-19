# Task: UC-11 — VerifyTwoFactor

## 📋 Resumo
A funcionalidade `VerifyTwoFactor` complementa o fluxo de Autenticação para usuários que possuem o recurso de Dupla Autenticação (2FA) ativado. Quando o `AuthenticateUserUseCase` (UC-02) detecta que o usuário possui 2FA, ele não devolve os tokens finais de acesso. Em vez disso, ele retorna um token temporário (`TWO_FACTOR`) e pede que o usuário forneça o código de verificação. Este Use Case (UC-11) recebe o token temporário e o código inserido pelo usuário, valida-os e, se estiverem corretos, finalmente concede a sessão e os tokens reais (Access Token e Refresh Token).

## 🎯 Objetivo
Receber um token do tipo `TWO_FACTOR` e um código de segurança (OTP/Código). Validar o token e o código de forma atômica contra a tabela `user_tokens`. Em caso de sucesso, marcar o token como utilizado, registrar um `LoginHistory` (status sucesso), gerar uma nova `Session` e devolver os tokens de acesso finais. Em caso de falhas excessivas, pode-se registrar tentativas falhas no `LoginHistory`.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `MÉDIA` (P2)
* **Endpoint:** `POST /v1/auth/2fa/verify`
* **Tabelas do Banco:** `users.user_tokens`, `users.sessions`, `users.login_history`, `users.users`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. *(Nenhuma entidade nova de domínio é necessária, utilizaremos `User`, `UserToken`, `Session` e `LoginHistory` já existentes).*

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/IVerifyTwoFactorUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/VerifyTwoFactorUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/VerifyTwoFactorInput.java`
4. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/VerifyTwoFactorOutput.java`

### `infrastructure`
1. *(Infraestrutura de banco já foi implementada e testada anteriormente).*

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar rota `POST /v1/auth/2fa/verify`)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Adicionar o `@Bean` do caso de uso)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* Trabalharemos com o `UserToken`. O código (OTP) fornecido pelo usuário deve corresponder ao `tokenHash` ou o `tokenHash` pode representar a união do token temporário e do código OTP de alguma forma, dependendo da implementação escolhida no UC-02.
* *Premissa adotada para este UC:* O input terá um `token` e um `code`. O sistema irá buscar o `UserToken` através do hash gerado a partir do `token`. Uma vez encontrado o `UserToken` do tipo `TWO_FACTOR`, o sistema deve validar o `code` contra uma chave secreta associada ao usuário (TOTP) ou o `code` pode ser o próprio conteúdo do token (caso o 2FA seja envio de código por email/SMS). Assumiremos a abordagem de envio de código (o `code` hasheado está no `tokenHash` e o identificador do token é outro, ou o token contém o userId).
* *Abordagem Simplificada (Para a base atual):* O input recebe um `token` (que é o ID da transação 2FA) e o `code`. Na verdade, se o `code` foi salvo na tabela `user_tokens` via hash, buscaremos pelo hash do `code` + `token`.

**Vamos padronizar a lógica:**
No momento em que o login exige 2FA, ele gera um `UserToken` onde o `tokenHash` armazena o hash do `code` gerado. O `token` devolvido na API é o ID desse `UserToken`. Portanto:
1. Buscar o `UserToken` por ID (que virá no payload como `token_id` ou `transaction_id`).
2. Validar se `tokenProvider.hashToken(input.code()) == userToken.getTokenHash()`.

### 2. Contrato de Entrada/Saída (Application)
* **`VerifyTwoFactorInput` (Record):** `transactionId` (String), `code` (String), `ipAddress` (String), `userAgent` (String).
* **`VerifyTwoFactorOutput` (Record):** `accessToken` (String), `refreshToken` (String), `userId` (String), `roles` (List<String>).

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar se `transactionId` e `code` não são nulos/vazios.
3. Buscar o `UserToken` por ID (usando `userTokenGateway.findById(transactionId)`). Como nosso gateway atual só busca por `tokenHash`, precisaremos **adicionar** o método `findById` no `IUserTokenGateway` e implementá-lo no `UserTokenPostgresGateway`.
4. Se o token não existir, ou for de tipo diferente de `"TWO_FACTOR"`, retornar erro genérico (`AuthError.INVALID_CREDENTIALS`).
5. Se expirado ou já utilizado, retornar erro (`AuthError.INVALID_CREDENTIALS`).
6. Fazer o hash do `code` informado e comparar com `userToken.getTokenHash()`. Se forem diferentes, registrar `LoginHistory` como falha, e retornar erro.
7. Buscar o usuário pelo `userToken.getUserId()`. Verificar se está habilitado e não bloqueado.
8. Marcar o token como usado: `userToken.markAsUsed()`.
9. Gerar Access Token e Refresh Token.
10. Criar a sessão (`Session.create(...)`) e o registro de sucesso (`LoginHistory.recordSuccess(...)`).
11. Executar tudo em transação (`transactionManager.execute(...)`):
    * Atualizar o `UserToken`.
    * Inserir `Session`.
    * Inserir `LoginHistory`.
12. Retornar `Right(new VerifyTwoFactorOutput(...))`.

### 4. Persistência (Infrastructure)
* **Ajuste Mínimo:** Adicionar `Optional<UserToken> findById(UserTokenID id)` no `IUserTokenGateway` e implementá-lo no `UserTokenPostgresGateway` usando `repository.findById()`.

### 5. Roteamento e Injeção (API)
* Criar o `@Bean` no `UsersModuleConfig`.
* Adicionar rota `POST /v1/auth/2fa/verify` no `AuthController` recebendo o `VerifyTwoFactorRequest`.
* Em caso de falha (`Left`), devolver `400 Bad Request` ou `401 Unauthorized` dependendo do erro.
* Em caso de sucesso, retornar `200 OK` com os tokens.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `AuthError.INVALID_CREDENTIALS` | O transaction_id não existe, expirou, já foi usado ou o code está incorreto | `401 Unauthorized` |
| `AuthError.ACCOUNT_DISABLED` | A conta foi desativada | `403 Forbidden` |

---

## 🌐 Contrato da API REST

### Request
```json
{
  "transaction_id": "f8a7c2e3-...",
  "code": "123456"
}
```

### Response (Sucesso)
```json
{
  "data": {
    "access_token": "eyJhb...",
    "refresh_token": "refresh...",
    "user_id": "f8a7c2e3...",
    "roles": ["ROLE_USER"]
  },
  "success": true,
  "timestamp": "2026-04-06T00:00:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. Modificar `IUserTokenGateway` e a infraestrutura para suportar a busca de Token por ID.
2. Contratos: Input, Output e `IVerifyTwoFactorUseCase`.
3. Implementação do `VerifyTwoFactorUseCase`.
4. `@Bean` configuration.
5. Endpoint `POST /v1/auth/2fa/verify` na API.
6. Testes unitários e de integração.