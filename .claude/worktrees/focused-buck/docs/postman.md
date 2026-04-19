# Guia Atualizado da API no Postman

Este documento reflete o estado atual do worktree `focused-buck`, considerando os controllers reais em `modules/api`, o `SecurityConfig` e o `server.servlet.context-path: /api`.

## 1. O que mudou nesta revisão

- O endpoint `UpdateProfile` foi analisado no `UserController.java`.
- O inventário foi reduzido aos endpoints realmente expostos neste worktree.
- As rotas de profile, address, 2FA, catalog e upload que não existem neste worktree foram removidas dos testes sugeridos.
- A seção de Postman agora deixa claro quais rotas públicas existem no código e quais rotas só aparecem no `SecurityConfig`.

## 2. Observações importantes

### 2.1. Base da aplicação

- Host local: `http://localhost:8080`
- Context path global: `/api`

Endpoints com `@RequestMapping("/v1/...")` ficam em:

```text
http://localhost:8080/api/v1/...
```

### 2.2. Controllers reais neste worktree

Hoje existem apenas estes controllers em `modules/api`:

- `AuthController`
- `UserController`
- `CouponController`

### 2.3. Status do UpdateProfile

O endpoint `PUT /v1/users/me/profile` não está implementado neste worktree.

No `UserController.java`, existe apenas:

```text
GET /v1/users/me
```

Também há beans de `GetProfileUseCase` e `UpdateProfileUseCase` comentados em `UseCaseConfig.java`. Portanto, uma request como esta deve retornar `404 Not Found` no estado atual:

```text
PUT {{host}}/api/v1/users/me/profile
```

### 2.4. Autenticação real

O `SecurityConfig` libera estas rotas:

- `/v1/auth/register`
- `/v1/auth/login`
- `/v1/auth/refresh`
- `/v1/auth/verify-email`
- `/v1/auth/logout`
- `/v1/auth/password/forgot`
- `/v1/auth/social/**`
- `/v1/auth/2fa/verify`
- `/actuator/health`
- `/swagger-ui/**`
- `/v3/api-docs/**`

Mas, neste worktree, só estas rotas públicas têm controller implementado:

- `/v1/auth/register`
- `/v1/auth/login`
- `/v1/auth/refresh`
- `/v1/auth/verify-email`
- `/v1/auth/logout`

Todo o restante exige JWT Bearer.

## 3. Configurando o Postman

### 3.1. Environment

Crie um environment chamado `BTree Local` com estas variáveis:

| Variável | Valor inicial | Uso |
|---|---|---|
| `host` | `http://localhost:8080` | host da aplicação |
| `token` | | access token JWT |
| `refreshToken` | | refresh token |
| `couponId` | | cupom para teste de atualização |

### 3.2. Authorization

Na collection, configure:

- Type: `Bearer Token`
- Token: `{{token}}`

Nas requests públicas de auth, deixe `Authorization` como `No Auth` ou herde a collection sem token apenas depois de limpar `{{token}}`.

### 3.3. Script de captura de tokens

Use este script em `login` e `refresh`:

```javascript
const json = pm.response.json();

if (json.accessToken) pm.environment.set("token", json.accessToken);
if (json.refreshToken) pm.environment.set("refreshToken", json.refreshToken);
```

## 4. Inventário Real de Endpoints

### 4.1. Públicos implementados

| Grupo | Método | URL final |
|---|---|---|
| Auth | `POST` | `{{host}}/api/v1/auth/register` |
| Auth | `POST` | `{{host}}/api/v1/auth/login` |
| Auth | `POST` | `{{host}}/api/v1/auth/verify-email` |
| Auth | `POST` | `{{host}}/api/v1/auth/refresh` |
| Auth | `POST` | `{{host}}/api/v1/auth/logout` |

### 4.2. Protegidos por JWT

| Grupo | Método | URL final |
|---|---|---|
| Users | `GET` | `{{host}}/api/v1/users/me` |
| Coupons | `PUT` | `{{host}}/api/v1/coupons/{{couponId}}` |

### 4.3. Rotas liberadas no SecurityConfig, mas sem controller neste worktree

Essas URLs passam pela whitelist de segurança, mas não possuem endpoint implementado agora:

| Grupo | Método esperado | URL final |
|---|---|---|
| Auth | `POST` | `{{host}}/api/v1/auth/password/forgot` |
| Auth | `POST` | `{{host}}/api/v1/auth/social/{provider}` |
| Auth | `POST` | `{{host}}/api/v1/auth/2fa/verify` |

### 4.4. Rotas de profile não implementadas

Não crie requests ativas para estas rotas neste worktree:

| Grupo | Método | URL final | Resultado esperado hoje |
|---|---|---|---|
| Profile | `GET` | `{{host}}/api/v1/users/me/profile` | `404 Not Found` |
| Profile | `PUT` | `{{host}}/api/v1/users/me/profile` | `404 Not Found` |

## 5. Payloads úteis para teste

### 5.1. Auth

#### Registrar usuário

`POST {{host}}/api/v1/auth/register`

```json
{
  "username": "joao.silva",
  "email": "joao.silva@email.com",
  "password": "Senha@123"
}
```

Resposta esperada: `201 Created`.

#### Login

`POST {{host}}/api/v1/auth/login`

```json
{
  "identifier": "joao.silva@email.com",
  "password": "Senha@123"
}
```

Resposta esperada: `200 OK`, com `accessToken` e `refreshToken`.

#### Refresh

`POST {{host}}/api/v1/auth/refresh`

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

Resposta esperada: `200 OK`, com novo par de tokens.

#### Verificar e-mail

`POST {{host}}/api/v1/auth/verify-email`

```json
{
  "token": "TOKEN_DE_VERIFICACAO"
}
```

Resposta esperada: `204 No Content`.

#### Logout

`POST {{host}}/api/v1/auth/logout`

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

Resposta esperada: `204 No Content`.

### 5.2. Users

#### Obter usuário atual

`GET {{host}}/api/v1/users/me`

Authorization: `Bearer {{token}}`

Resposta esperada: `200 OK`.

### 5.3. Profile

#### Atualizar perfil

Não disponível neste worktree.

O código analisado em `UserController.java` não possui:

```java
@PutMapping("/me/profile")
```

Se você enviar a request abaixo, o resultado esperado hoje é `404 Not Found`:

```text
PUT {{host}}/api/v1/users/me/profile
```

Payload planejado em outros worktrees, mas não atendido por este controller:

```json
{
  "first_name": "Joao",
  "last_name": "Silva",
  "cpf": "123.456.789-00",
  "birth_date": "1990-05-15",
  "gender": "MALE",
  "preferred_language": "pt-BR",
  "preferred_currency": "BRL",
  "newsletter_subscribed": true
}
```

### 5.4. Coupons

#### Atualizar cupom

`PUT {{host}}/api/v1/coupons/{{couponId}}`

Authorization: `Bearer {{token}}`

```json
{
  "description": "Cupom atualizado pelo Postman",
  "discountValue": 15.00,
  "minOrderValue": 100.00,
  "maxDiscountAmount": 50.00,
  "maxUses": 100,
  "maxUsesPerUser": 1,
  "startsAt": "2026-04-18T00:00:00Z",
  "expiresAt": "2026-12-31T23:59:59Z",
  "eligibleCategoryIds": [],
  "eligibleProductIds": [],
  "eligibleBrandIds": [],
  "eligibleUserIds": []
}
```

Campos imutáveis pelo endpoint:

- `code`
- `coupon_type`
- `coupon_scope`
- `status`

## 6. Ordem sugerida de teste manual

1. Registrar usuário.
2. Fazer login e capturar `token` e `refreshToken`.
3. Chamar `GET {{host}}/api/v1/users/me` com Bearer token.
4. Chamar `POST {{host}}/api/v1/auth/refresh`.
5. Se houver cupom existente no banco, preencher `couponId` e chamar `PUT {{host}}/api/v1/coupons/{{couponId}}`.
6. Chamar `POST {{host}}/api/v1/auth/logout`.

## 7. Resumo da análise

O projeto neste worktree expõe 7 endpoints de negócio em `modules/api`:

- 5 endpoints de auth
- 1 endpoint de usuário atual
- 1 endpoint de atualização de cupom

Principais desvios encontrados:

- `UpdateProfile` não está exposto no `UserController.java`.
- Os beans de profile em `UseCaseConfig.java` estão comentados.
- O `SecurityConfig` libera algumas rotas que não possuem controller neste worktree.
- O `docs/postman.md` anterior descrevia endpoints de outros worktrees ou versões da API e poderia induzir testes em URLs inexistentes.
