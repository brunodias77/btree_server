# Task: UC-09 — LoginWithSocialProvider

## 📋 Resumo
Esta funcionalidade permite que usuários se cadastrem ou façam login utilizando provedores de identidade OAuth2 externos, como Google, Facebook, Apple, etc. Ao invés de criarem uma senha, o sistema confia na validação do token do provedor social, registrando o ID externo do usuário na tabela `user_social_logins`. Caso seja a primeira vez, uma nova conta `User` é gerada. Caso contrário, a sessão é criada diretamente.

## 🎯 Objetivo
Receber um token de acesso ou ID token de um provedor social (ex: JWT do Google), validá-lo contra o serviço externo (via `SocialProviderGateway`) e extrair as informações básicas (email, nome, providerUserId). Em seguida:
1. Buscar se já existe uma vinculação na tabela `user_social_logins`. Se existir, gerar a sessão para esse usuário.
2. Se não existir, verificar se o e-mail já existe na base de dados (`users`). Se sim, associar o provedor social a este usuário existente.
3. Se não existir o e-mail, registrar um novo `User` (com `emailVerified = true`) e criar a vinculação no `user_social_logins`.
4. Em qualquer cenário de sucesso, gerar Access Token e Refresh Token para o usuário final, juntamente com o registro do `LoginHistory`.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `MÉDIA` (P2)
* **Endpoint:** `POST /v1/auth/social/{provider}`
* **Tabelas do Banco:** `users.user_social_logins`, `users.users`, `users.sessions`, `users.login_history`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/entities/UserSocialLogin.java` (Entidade que representa o link do provedor social com a conta)
2. `domain/src/main/java/com/btree/domain/users/identifiers/UserSocialLoginID.java` (Identificador único)
3. `domain/src/main/java/com/btree/domain/users/gateways/IUserSocialLoginGateway.java` (Repositório da tabela de relacionamentos)
4. `domain/src/main/java/com/btree/domain/users/gateways/ISocialProviderGateway.java` (Contrato para os provedores externos, ex: Google, validar os tokens)
5. `domain/src/main/java/com/btree/domain/users/models/SocialUserProfile.java` (Record para devolver os dados extraídos pelo Provedor Social)

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/social/ILoginWithSocialProviderUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/social/LoginWithSocialProviderUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/social/LoginWithSocialProviderInput.java`
4. `application/src/main/java/com/btree/application/users/usecases/auth/social/LoginWithSocialProviderOutput.java`

### `infrastructure`
1. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/entities/UserSocialLoginJpaEntity.java`
2. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/repositories/UserSocialLoginRepository.java`
3. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/gateways/UserSocialLoginPostgresGateway.java`
4. `infrastructure/src/main/java/com/btree/infrastructure/users/gateways/GoogleSocialProviderGateway.java` (Implementação do contrato de validação do Google via API ou SDK do Google)

### `api`
1. `api/src/main/java/com/btree/api/users/AuthController.java` (Adicionar rota `POST /v1/auth/social/{provider}`)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Adicionar o `@Bean` do caso de uso)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* **`UserSocialLogin` (Entity):** Mapeia a tabela. Possui `id`, `userId`, `provider` (ex: "google", "facebook"), `providerUserId` (o ID único gerado pelo Google) e `createdAt`.
* **`SocialUserProfile` (Value Object/Record):** Contém `providerUserId`, `email`, `firstName`, `lastName`, `pictureUrl`.
* A entidade `User` será utilizada com a Factory de criação caso precise registrar uma conta nova. Porém, como a senha é obrigatória hoje na factory, talvez precisemos ajustar a factory `User.create` para aceitar `passwordHash` como `null` ou criar uma nova factory `User.createFromSocial()`.

### 2. Contrato de Entrada/Saída (Application)
* **`LoginWithSocialProviderInput` (Record):** `provider` (String), `token` (String - JWT do provedor), `ipAddress` (String), `userAgent` (String).
* **`LoginWithSocialProviderOutput` (Record):** `accessToken` (String), `refreshToken` (String), `userId` (String), `roles` (List<String>).

### 3. Lógica do Use Case (Application)
1. Instanciar `Notification.create()`.
2. Validar `provider` (verificar se suportamos) e `token`. Se falhar, retornar erro.
3. Chamar `socialProviderGateway.validateTokenAndGetProfile(provider, token)`.
4. Se a validação no provedor externo falhar (token falso, expirado ou providerUserId nulo), dar append em `AuthError.INVALID_SOCIAL_TOKEN` e retornar `Left(notification)`.
5. Com o `SocialUserProfile` em mãos, buscar o `UserSocialLogin` pelo `provider` e `providerUserId`:
   * `userSocialLoginGateway.findByProviderAndProviderUserId(provider, providerUserId)`
6. **Fluxo 1: Vínculo já existe**
   * Pega o `userId` do vínculo e busca o `User`. Se inativo ou bloqueado, retornar falha de acesso (`AuthError.ACCOUNT_DISABLED`).
7. **Fluxo 2: Vínculo não existe**
   * Busca um usuário pelo e-mail retornado pelo provedor: `userGateway.findByEmail(socialProfile.email())`.
   * **Se o usuário com o email já existir**: Associa o provedor a essa conta.
   * **Se não existir**: Cria um novo `User` usando `User.createFromSocial()` marcando `emailVerified = true`.
   * Em ambos os casos do Fluxo 2, cria o `UserSocialLogin` e salva.
8. Após garantir o `User`, gerar sessão e refresh token:
   * Criar a entidade `Session` e salvar.
   * Gerar `AccessToken` e `RefreshToken` via `TokenProvider`.
   * Gravar no `LoginHistory` (status SUCCESS).
9. Executar as mutações (Criação de usuário, Criação de vínculo, Criação de Sessão, Criação de Histórico) de forma atômica no `transactionManager.execute(() -> { ... })`.
10. Retornar `Right(new LoginWithSocialProviderOutput(...))`.

### 4. Persistência (Infrastructure)
* Criar a tabela `users.user_social_logins` via Flyway.
* Mapear `UserSocialLoginJpaEntity` vinculando `@Table(name = "user_social_logins", schema = "users")`.
* A implementação do `GoogleSocialProviderGateway` pode utilizar o pacote `google-api-client` ou fazer chamadas HTTP simples à API do Google (ex: `https://oauth2.googleapis.com/tokeninfo?id_token=...`) para verificar a assinatura e audiência do token.

### 5. Roteamento e Injeção (API)
* Adicionar o `@Bean` do `loginWithSocialProviderUseCase` no `UsersModuleConfig`.
* Adicionar a rota `POST /v1/auth/social/{provider}` no `AuthController`.
* Desempacotar o `Either`. Se falhar, mapear para `401 Unauthorized` ou `422 Unprocessable Entity`.
* Em caso de sucesso, retornar `200 OK` e um JSON via `ApiResponse.success(output)`.

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `AuthError.INVALID_SOCIAL_TOKEN` | O token enviado não é reconhecido pelo provedor externo | `401 Unauthorized` |
| `AuthError.UNSUPPORTED_PROVIDER` | O provedor enviado no path parameter não é suportado | `400 Bad Request` |
| `AuthError.ACCOUNT_DISABLED` | O usuário correspondente ao e-mail ou vínculo está inativo | `403 Forbidden` |

---

## 🌐 Contrato da API REST

### Request
```json
{
  "token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjNm..."
}
```
*(Path Parameter: `provider` = `google`)*

### Response (Sucesso)
```json
{
  "data": {
    "access_token": "eyJhbG...",
    "refresh_token": "d7b9c2a3-f5e6-48b9...",
    "user_id": "c1f3a2b1...",
    "roles": ["customer"]
  },
  "success": true,
  "timestamp": "2026-04-06T12:00:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. Migration do banco de dados para a tabela `users.user_social_logins`.
2. Entidade de Domínio `UserSocialLogin`, Value Objects e novos métodos em `User`.
3. Contratos de Gateway de integração (`ISocialProviderGateway`) e de Repositório (`IUserSocialLoginGateway`).
4. Contratos: Input, Output e `ILoginWithSocialProviderUseCase`.
5. Implementação do `LoginWithSocialProviderUseCase`.
6. Implementação da Infraestrutura: JPA, PostgresGateway e o Gateway do Google.
7. `@Bean` configuration.
8. Endpoint `POST /v1/auth/social/{provider}` na API.
9. Testes unitários e de integração.