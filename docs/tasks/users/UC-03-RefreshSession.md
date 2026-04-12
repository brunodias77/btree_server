# Task: UC-03 — RefreshSession

## 📋 Resumo
Esta funcionalidade permite que um usuário autenticado renove seu *Access Token* expirado (ou prestes a expirar) enviando um *Refresh Token* válido. O sistema valida o token fornecido contra as sessões ativas no banco de dados. Em caso de sucesso, uma nova sessão é criada (e a antiga pode ser mantida ou revogada dependendo da política, optaremos por rotacionar) e novos tokens são retornados.

## 🎯 Objetivo
Validar o Refresh Token recebido, garantir que a sessão vinculada a ele está ativa e não expirada/revogada, e emitir um novo par de tokens (Access + Refresh) para manter o usuário logado sem necessidade de re-autenticação manual.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `CRÍTICO` (P0)
* **Endpoint:** `POST /v1/auth/refresh`
* **Tabelas do Banco:** `users.sessions`, `users.users`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/gateways/ISessionGateway.java` (Adicionar método `findByRefreshTokenHash` e `update`)
2. `domain/src/main/java/com/btree/domain/users/errors/AuthError.java` (Adicionar `INVALID_REFRESH_TOKEN` ou `SESSION_EXPIRED`)

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/refresh/IRefreshSessionUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/refresh/RefreshSessionUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/refresh/RefreshSessionInput.java`
4. `application/src/main/java/com/btree/application/users/usecases/auth/refresh/RefreshSessionOutput.java`

### `infrastructure`
1. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/repositories/SessionRepository.java` (Adicionar `findByRefreshTokenHash`)
2. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/gateways/SessionPostgresGateway.java` (Implementar novos métodos do gateway)

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar endpoint `/refresh`)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Registrar Bean do `RefreshSessionUseCase`)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* **`Session` (AggregateRoot):** Reutilizaremos a entidade `Session`. Precisaremos garantir que métodos de comportamento como `revoke()` e `isActive()` estão corretos.
* **Erros de Domínio:** Criar `AuthError.INVALID_REFRESH_TOKEN` (usado quando o token não existe, é inválido, está expirado ou revogado).

### 2. Contrato de Entrada/Saída (Application)
* **`RefreshSessionInput` (Record):** `refreshToken`, `ipAddress`, `userAgent`.
* **`RefreshSessionOutput` (Record):** `accessToken`, `refreshToken`, `expiresIn`.

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar se o `refreshToken` fornecido é válido e não expirou estruturalmente (via `TokenProvider.isTokenValid` e `isTokenType("refresh")`). Se inválido, retornar `Left(INVALID_REFRESH_TOKEN)`.
3. Gerar o hash do `refreshToken` fornecido usando `TokenProvider.hashToken()`.
4. Buscar a sessão via `ISessionGateway.findByRefreshTokenHash(hash)`. Se não encontrar, retornar `Left(INVALID_REFRESH_TOKEN)`.
5. Verificar se a sessão está ativa (`session.isActive()`). Se estiver revogada ou expirada, retornar `Left(INVALID_REFRESH_TOKEN)`.
6. Buscar o usuário associado à sessão (`IUserGateway.findById(session.getUserId())`). Se não existir ou estiver desativado (`!user.isEnabled()`), retornar `Left(INVALID_REFRESH_TOKEN)`.
7. **Rotação de Sessão:** 
   * Revogar a sessão atual (`session.revoke()`).
   * Gerar novos tokens (`TokenProvider.generateAccessToken`, `generateRefreshToken`).
   * Hashear o novo Refresh Token.
   * Criar uma nova entidade `Session` com os novos dados e metadados de dispositivo atualizados.
8. Executar persistência via `transactionManager.execute(() -> { ... })`:
   * Atualizar a sessão antiga (`sessionGateway.update(oldSession)`).
   * Criar a nova sessão (`sessionGateway.create(newSession)`).
9. Retornar `Right(new RefreshSessionOutput(...))`.

### 4. Persistência (Infrastructure)
* No `SessionRepository`, adicionar o método `Optional<SessionJpaEntity> findByRefreshTokenHash(String hash)`.
* No `SessionPostgresGateway`, implementar `findByRefreshTokenHash` convertendo de `JpaEntity` para `Aggregate`.
* No `SessionPostgresGateway`, implementar o método `update(Session session)` para persistir a revogação da sessão antiga.

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` do `RefreshSessionUseCase` no `UsersModuleConfig`.
* No `AuthController`, mapear `POST /v1/auth/refresh`.
* O payload do endpoint pode receber o token no Body (ex: `{"refresh_token": "..."}`).
* Desempacotar o `Either`. Em caso de erro (`Left`), retornar `401 Unauthorized` com `ApiResponse.error()`.
* Em caso de sucesso, retornar `200 OK` com `ApiResponse.success()`.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `AuthError.INVALID_REFRESH_TOKEN` | Token estruturalmente inválido, não encontrado, revogado ou expirado | `401 Unauthorized` |
| `AuthError.ACCOUNT_DISABLED` | Usuário dono da sessão foi desativado/bloqueado | `401 Unauthorized` |

---

## 🌐 Contrato da API REST

### Request
```json
{
  "refresh_token": "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9..."
}
```

### Response (Sucesso)
```json
{
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "def50200a88b577a11e03...",
    "expires_in": 3600
  },
  "success": true,
  "timestamp": "2026-04-06T12:00:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. Atualizar o `AuthError` com os novos tipos de erro.
2. Contratos: Input, Output e `IRefreshSessionUseCase`.
3. Adicionar métodos necessários em `ISessionGateway`.
4. Implementação do `RefreshSessionUseCase` garantindo a rotação de tokens.
5. Adicionar queries no `SessionRepository` e implementar os métodos novos no `SessionPostgresGateway`.
6. `@Bean` configuration no `UsersModuleConfig`.
7. Endpoint `/refresh` no `AuthController`.
8. Testes (UseCase e API).