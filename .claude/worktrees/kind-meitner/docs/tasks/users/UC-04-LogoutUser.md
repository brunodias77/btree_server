# Task: UC-04 — LogoutUser

## 📋 Resumo
O caso de uso `LogoutUser` permite que um usuário encerre de forma segura a sua sessão ativa. Como o sistema baseia-se em JWT (Stateless), não é possível invalidar o *Access Token* diretamente, mas podemos revogar a entidade `Session` no banco de dados atrelada ao *Refresh Token*. Dessa forma, impedimos que o usuário ou qualquer invasor gere novos tokens. 

## 🎯 Objetivo
Buscar a sessão correspondente ao `refresh_token` fornecido e executar o método `revoke()`, marcando-a como inválida no banco de dados.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `CRÍTICO` (P0)
* **Endpoint:** `POST /v1/auth/logout`
* **Tabelas do Banco:** `users.sessions`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. *(Nenhuma alteração esperada, pois `Session.revoke()`, `AuthError.INVALID_REFRESH_TOKEN` e `ISessionGateway.findByRefreshTokenHash` já foram criados no UC-03)*

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/logout/ILogoutUserUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/logout/LogoutUserUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/logout/LogoutUserInput.java`
4. *(Não há necessidade de `Output` para este UseCase, pode retornar `Void` ou `Boolean`)*

### `infrastructure`
1. *(Nenhuma alteração esperada, pois os repositórios já foram configurados)*

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar endpoint `/logout`)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Registrar Bean do `LogoutUserUseCase`)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* Não há novas entidades a serem criadas. O caso de uso reutilizará a entidade `Session` e invocará seu método `revoke()`.

### 2. Contrato de Entrada/Saída (Application)
* **`LogoutUserInput` (Record):** `refreshToken` (String).
* **Saída:** O método `execute` pode retornar `Either<Notification, Void>` ou um boolean, indicando sucesso da operação.

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar se o `refreshToken` está presente. Se não estiver, retornar `Left(AuthError.INVALID_REFRESH_TOKEN)`.
3. Extrair o hash do token usando `TokenProvider.hashToken(refreshToken)`.
4. Buscar a sessão no gateway: `sessionGateway.findByRefreshTokenHash(hash)`.
5. Se a sessão não existir, **retornar sucesso silenciosamente** (idempotência: se já não existe, o objetivo do logout já foi alcançado).
6. Se a sessão existir e estiver ativa (`session.isActive()`), chamar `session.revoke()`.
7. Persistir a alteração encapsulada em uma transação:
   * `transactionManager.execute(() -> sessionGateway.update(session))`
8. Retornar `Right(null)`.

### 4. Persistência (Infrastructure)
* Nenhuma ação necessária. Apenas reutilizar o método `update(session)` já existente no `SessionPostgresGateway`.

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` no arquivo `UsersModuleConfig.java`.
* No `AuthController.java`, criar o método `@PostMapping("/logout")`.
* O endpoint **deve exigir autenticação** (o JWT Access Token deve estar no header, além de enviar o Refresh Token no Body).
* Extrair o `refresh_token` do RequestBody e repassar para o `LogoutUserUseCase`.
* Retornar `204 No Content` ou `200 OK` (com `ApiResponse.success(null)`) em caso de sucesso.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `AuthError.INVALID_REFRESH_TOKEN` | Token ausente ou estruturalmente corrompido | `400 Bad Request` |

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
  "data": null,
  "success": true,
  "timestamp": "2026-04-06T12:00:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. Criar os contratos na camada de aplicação (`LogoutUserInput`, `ILogoutUserUseCase`).
2. Implementar a lógica no `LogoutUserUseCase`.
3. Criar a configuração do `@Bean` em `UsersModuleConfig`.
4. Adicionar o endpoint `/logout` em `AuthController`.
5. Testar a revogação e garantir que o `/refresh` falhará caso seja chamado em seguida.