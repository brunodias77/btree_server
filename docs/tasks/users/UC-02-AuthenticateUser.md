# Task: UC-02 — AuthenticateUser

## 📋 Resumo
Esta funcionalidade permite que usuários existentes façam login no sistema fornecendo seu e-mail e senha. O sistema valida as credenciais e, se corretas, retorna um *Access Token* (JWT de curta duração) e um *Refresh Token* (JWT de longa duração armazenado em banco para renovação de sessão). Além disso, registra a tentativa de login no histórico para auditoria e segurança.

## 🎯 Objetivo
Autenticar o usuário, criar uma sessão ativa e registrar a tentativa de acesso (sucesso ou falha) no histórico de logins.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `CRÍTICO` (P0)
* **Endpoint:** `POST /v1/auth/login`
* **Tabelas do Banco:** `users.users`, `users.sessions`, `users.login_history`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/entities/Session.java` (Criar se não existir)
2. `domain/src/main/java/com/btree/domain/users/entities/LoginHistory.java` (Criar se não existir)
3. `domain/src/main/java/com/btree/domain/users/gateways/ISessionGateway.java`
4. `domain/src/main/java/com/btree/domain/users/gateways/ILoginHistoryGateway.java`
5. `domain/src/main/java/com/btree/domain/users/errors/AuthError.java` (Erros específicos de autenticação)
6. `domain/src/main/java/com/btree/domain/users/events/SessionCreatedEvent.java`

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/authenticate/IAuthenticateUserUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/authenticate/AuthenticateUserUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/authenticate/AuthenticateUserInput.java`
4. `application/src/main/java/com/btree/application/users/usecases/auth/authenticate/AuthenticateUserOutput.java`

### `infrastructure`
1. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/entities/SessionJpaEntity.java`
2. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/entities/LoginHistoryJpaEntity.java`
3. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/repositories/SessionRepository.java`
4. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/repositories/LoginHistoryRepository.java`
5. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/gateways/SessionPostgresGateway.java`
6. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/gateways/LoginHistoryPostgresGateway.java`

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar endpoint)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Registrar Bean)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* **`Session` (AggregateRoot):** Representa uma sessão ativa. Deve conter `refresh_token_hash`, metadados do dispositivo (Device Info), status (ativa/revogada) e timestamps.
  * **Factory:** `Session.create(userId, refreshTokenHash, deviceInfo, expiration)`
* **`LoginHistory` (Entity):** Registro imutável de tentativa de login.
  * **Factory:** `LoginHistory.recordSuccess(...)` ou `LoginHistory.recordFailure(...)`.
* Validação Fail-Fast: Se algum dado obrigatório faltar nas factories, acumular no `Notification` e lançar `ValidationException` se `notification.hasError()`.

### 2. Contrato de Entrada/Saída (Application)
* **`AuthenticateUserInput` (Record):** `email`, `password`, `deviceInfo` (IP, User-Agent).
* **`AuthenticateUserOutput` (Record):** `accessToken`, `refreshToken`, `expiresIn`.

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Buscar usuário por e-mail via `IUserGateway`. Se não existir, adicionar `AuthError.INVALID_CREDENTIALS` (evitar dizer que e-mail não existe por segurança) e retornar `Left`.
3. Validar se a conta não está bloqueada (`user.isAccountLocked()`). Se estiver, retornar `Left`.
4. Verificar hash da senha via `PasswordHasher.verify()`.
5. Se a senha for **inválida**:
   * Incrementar falhas (`user.incrementAccessFailed()`).
   * Se ultrapassar limite, bloquear conta.
   * Gravar `LoginHistory` de falha.
   * Executar tudo via `transactionManager`. Retornar `Left(INVALID_CREDENTIALS)`.
6. Se a senha for **válida**:
   * Resetar falhas (`user.resetAccessFailed()`).
   * Gerar tokens JWT (via port/provider de token).
   * Criar entidade `Session` com o hash do Refresh Token.
   * Criar `LoginHistory` de sucesso.
   * Persistir `User` (update), `Session` e `LoginHistory` num único bloco `transactionManager.execute(() -> { ... })`.
7. Retornar `Right(new AuthenticateUserOutput(...))`.

### 4. Persistência (Infrastructure)
* Criar as entidades JPA `SessionJpaEntity` e `LoginHistoryJpaEntity`.
* Lembrar de mapear os schemas corretamente: `@Table(name = "sessions", schema = "users")`.
* A tabela `login_history` no schema é particionada. A infra não muda a forma de inserir, mas o Hibernate deve mapear as chaves compostas/corretas.
* No `SessionPostgresGateway.create(session)`, chamar `domainEventPublisher.publishAll(session.getDomainEvents())`.

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` do `AuthenticateUserUseCase` no `UsersModuleConfig`.
* No `AuthController`, mapear `POST /v1/auth/login`.
* Desempacotar o `Either`. Em caso de erro, retornar `401 Unauthorized` ou `422` com `ApiResponse.error()`.
* Em caso de sucesso, retornar `200 OK` com `ApiResponse.success()`.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `AuthError.INVALID_CREDENTIALS` | E-mail não encontrado ou senha incorreta | `401 Unauthorized` |
| `AuthError.ACCOUNT_LOCKED` | Conta bloqueada por excesso de tentativas | `403 Forbidden` |
| `AuthError.ACCOUNT_DISABLED` | Conta desativada (enabled = false) | `403 Forbidden` |

---

## 🌐 Contrato da API REST

### Request
```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!",
  "device_info": {
    "ip_address": "192.168.1.1",
    "user_agent": "Mozilla/5.0..."
  }
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
1. Entidades de Domínio (`Session`, `LoginHistory`) e `AuthError`.
2. Contratos: Input, Output e `IAuthenticateUserUseCase`.
3. Interfaces dos Gateways (`ISessionGateway`, `ILoginHistoryGateway`) e porta de Token (`TokenProvider`).
4. Implementação do `AuthenticateUserUseCase`.
5. `JpaEntity`, `Repository` e Implementação dos Gateways na Infra.
6. `@Bean` configuration no `UsersModuleConfig`.
7. `Controller` na API.
8. Testes unitários (UseCase) e de integração (API).