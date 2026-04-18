# Postman — Guia de Testes da API btree

## Configuração Inicial

### Environment Variables

Crie um Environment no Postman chamado **btree-local** com as seguintes variáveis:

| Variable | Initial Value | Description |
|---|---|---|
| `base_url` | `http://localhost:8080/api` | Base URL da API |
| `access_token` | _(vazio)_ | Preenchido automaticamente após login |
| `refresh_token` | _(vazio)_ | Preenchido automaticamente após login |
| `user_id` | _(vazio)_ | Preenchido automaticamente após login |

### Authorization Global

Em todas as requisições protegidas, configure:

- **Auth Type:** `Bearer Token`
- **Token:** `{{access_token}}`

### Headers Padrão

Para todas as requisições com body:

```
Content-Type: application/json
Accept: application/json
```

---

## Formato de Erro Padrão

Todos os erros seguem o mesmo schema:

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Descrição do erro principal",
  "errors": ["Detalhe 1", "Detalhe 2"],
  "timestamp": "2026-04-18T12:00:00Z",
  "path": "/api/v1/..."
}
```

| Status | Significado |
|---|---|
| `400` | Dados de entrada inválidos (formato, tipo, obrigatoriedade) |
| `401` | Token ausente, inválido ou expirado |
| `403` | Autenticado, mas sem permissão |
| `404` | Recurso não encontrado |
| `409` | Conflito (email/username já existe, conflito de versão) |
| `422` | Regra de negócio violada |
| `500` | Erro interno inesperado |

---

## Contexto: Auth

**Base path:** `/v1/auth`  
**Segurança:** Todos os endpoints abaixo são **públicos** (sem JWT).

---

### 1. Registrar Usuário

**`POST /v1/auth/register`**

Cria uma nova conta de usuário. Após o registro, um e-mail de verificação é enviado.

**Request:**

```http
POST {{base_url}}/v1/auth/register
Content-Type: application/json
```

```json
{
  "username": "joao_silva",
  "email": "joao@exemplo.com",
  "password": "Senha@1234"
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `username` | Sim | 1–256 caracteres |
| `email` | Sim | Formato de e-mail válido |
| `password` | Sim | 8–256 caracteres |

**Response `201 Created`:**

```json
{
  "userId": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joao_silva",
  "email": "joao@exemplo.com",
  "createdAt": "2026-04-18T12:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente ou formato inválido |
| `409` | `username` ou `email` já cadastrado |
| `422` | Regra de negócio (ex: senha fraca) |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.test("userId presente", () => {
  const body = pm.response.json();
  pm.expect(body.userId).to.be.a("string");
  pm.environment.set("user_id", body.userId);
});
```

---

### 2. Login

**`POST /v1/auth/login`**

Autentica um usuário e retorna tokens de acesso e refresh. Se o usuário tiver 2FA ativo, retorna um `transactionId` em vez dos tokens.

**Request:**

```http
POST {{base_url}}/v1/auth/login
Content-Type: application/json
```

```json
{
  "identifier": "joao_silva",
  "password": "Senha@1234"
}
```

> O campo `identifier` aceita tanto **username** quanto **e-mail**.

**Response `200 OK` (sem 2FA):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresAt": "2026-04-18T12:15:00Z",
  "userId": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joao_silva",
  "email": "joao@exemplo.com",
  "requiresTwoFactor": null,
  "transactionId": null
}
```

**Response `200 OK` (com 2FA ativo):**

```json
{
  "accessToken": null,
  "refreshToken": null,
  "accessTokenExpiresAt": null,
  "userId": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joao_silva",
  "email": "joao@exemplo.com",
  "requiresTwoFactor": true,
  "transactionId": "tx_abc123def456"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente |
| `401` | Credenciais inválidas ou conta bloqueada |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
if (body.accessToken) {
  pm.environment.set("access_token", body.accessToken);
  pm.environment.set("refresh_token", body.refreshToken);
  pm.environment.set("user_id", body.userId);
  pm.test("Tokens salvos no environment", () => {
    pm.expect(pm.environment.get("access_token")).to.not.be.empty;
  });
}
```

---

### 3. Login Social (OAuth2)

**`POST /v1/auth/social/{provider}`**

Autentica um usuário via token OAuth2 de um provedor externo. Cria conta automaticamente se o e-mail ainda não existir, ou vincula ao usuário existente caso o e-mail já esteja cadastrado.

**Provedores suportados:** `google` (case-insensitive)

**Request:**

```http
POST {{base_url}}/v1/auth/social/google
Content-Type: application/json
```

```json
{
  "token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEx..."
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `token` | Sim | ID token ou access token emitido pelo provedor OAuth2; não pode ser vazio |
| `provider` (path) | Sim | Provedor OAuth2; atualmente apenas `google` |

**Response `200 OK`:**

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "a9c3b2f1e8d7c6b5a4f3e2d1c0b9a8f7...",
  "access_token_expires_at": "2026-04-18T12:15:00Z",
  "user_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joaosilva4829",
  "email": "joao@gmail.com",
  "roles": ["customer"]
}
```

> **Comportamento por fluxo:**
> - **Vínculo existente**: usuário já possui conta vinculada ao provedor → login direto.
> - **E-mail existente, sem vínculo**: vincula o provedor ao usuário existente → login direto.
> - **Usuário novo**: cria conta com `email_verified = true`, `password = null`, username derivado do perfil social (nome + sufixo de 4 dígitos aleatórios) e papel `customer`.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo `token` ausente ou em branco |
| `400` | Provedor (`{provider}`) não suportado |
| `401` | Token OAuth2 inválido ou expirado no provedor |
| `403` | Conta desativada (`enabled = false`) |
| `422` | Falha na validação do token pelo provedor (perfil não retornado) |
| `500` | Erro interno inesperado durante a transação |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("access_token presente", () => {
  pm.expect(body.access_token).to.be.a("string").and.not.be.empty;
  pm.environment.set("access_token", body.access_token);
});
pm.test("refresh_token presente", () => {
  pm.expect(body.refresh_token).to.be.a("string").and.not.be.empty;
  pm.environment.set("refresh_token", body.refresh_token);
});
pm.test("user_id presente", () => {
  pm.expect(body.user_id).to.be.a("string");
  pm.environment.set("user_id", body.user_id);
});
pm.test("roles é array não-vazio", () => {
  pm.expect(body.roles).to.be.an("array").and.not.be.empty;
});
```

---

### 4. Verificar E-mail

**`POST /v1/auth/verify-email`**

Confirma o e-mail do usuário usando o token recebido no e-mail de verificação.

**Request:**

```http
POST {{base_url}}/v1/auth/verify-email
Content-Type: application/json
```

```json
{
  "token": "a1b2c3d4e5f6..."
}
```

**Response `204 No Content`:** Body vazio.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Token ausente |
| `422` | Token inválido, expirado ou já utilizado |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 204", () => pm.response.to.have.status(204));
pm.test("Body vazio", () => pm.expect(pm.response.text()).to.be.empty);
```

---

### 5. Renovar Sessão (Token Rotation)

**`POST /v1/auth/refresh`**

Gera um novo par de tokens (`accessToken` + `refreshToken`). O refresh token anterior é invalidado (token rotation).

**Request:**

```http
POST {{base_url}}/v1/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "{{refresh_token}}"
}
```

**Response `200 OK`:**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresAt": "2026-04-18T12:30:00Z",
  "userId": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joao_silva",
  "email": "joao@exemplo.com"
}
```

> **Atenção:** O `refreshToken` retornado é **novo**. O token enviado na requisição não pode ser reutilizado.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Refresh token ausente |
| `422` | Sessão inválida, revogada ou expirada |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.environment.set("access_token", body.accessToken);
pm.environment.set("refresh_token", body.refreshToken);
pm.test("Tokens atualizados", () => {
  pm.expect(body.accessToken).to.be.a("string").and.not.be.empty;
  pm.expect(body.refreshToken).to.be.a("string").and.not.be.empty;
});
```

---

### 6. Logout

**`POST /v1/auth/logout`**

Revoga o refresh token, encerrando a sessão. O access token ainda é válido até expirar (15 min), mas sem possibilidade de renovação.

**Request:**

```http
POST {{base_url}}/v1/auth/logout
Content-Type: application/json
```

```json
{
  "refreshToken": "{{refresh_token}}"
}
```

**Response `204 No Content`:** Body vazio.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Refresh token ausente ou formato inválido |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 204", () => pm.response.to.have.status(204));
pm.environment.unset("access_token");
pm.environment.unset("refresh_token");
```

---

### 7. Solicitar Redefinição de Senha

**`POST /v1/auth/password/forgot`**

Envia um e-mail com link/token para redefinição de senha. Sempre retorna `200 OK` independente de o e-mail existir (proteção anti-enumeração).

**Request:**

```http
POST {{base_url}}/v1/auth/password/forgot
Content-Type: application/json
```

```json
{
  "email": "joao@exemplo.com"
}
```

**Response `200 OK`:** Body vazio.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `422` | E-mail ausente ou formato inválido |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
// Sempre 200 independente do e-mail existir ou não
```

---

### 8. Confirmar Redefinição de Senha

**`POST /v1/auth/password/reset`**

Redefine a senha do usuário usando o token recebido por e-mail. O token é de uso único e tem prazo de expiração.

**Request:**

```http
POST {{base_url}}/v1/auth/password/reset
Content-Type: application/json
```

```json
{
  "token": "a1b2c3d4e5f6...",
  "newPassword": "NovaSenha@5678"
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `token` | Sim | Token recebido no e-mail |
| `newPassword` | Sim | 8–256 caracteres |

**Response `204 No Content`:** Body vazio.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente ou formato inválido |
| `422` | Token inválido, expirado ou já utilizado; senha não atende aos requisitos |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 204", () => pm.response.to.have.status(204));
pm.test("Body vazio", () => pm.expect(pm.response.text()).to.be.empty);
```

---

## Contexto: Users

**Base path:** `/v1/users`  
**Segurança:** Todos os endpoints abaixo requerem **JWT válido** no header `Authorization: Bearer {{access_token}}`.

---

### 1. Obter Usuário Atual

**`GET /v1/users/me`**

Retorna os dados do usuário autenticado extraído do JWT.

**Request:**

```http
GET {{base_url}}/v1/users/me
Authorization: Bearer {{access_token}}
```

**Response `200 OK`:**

```json
{
  "id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joao_silva",
  "email": "joao@exemplo.com",
  "email_verified": true,
  "roles": ["ROLE_CUSTOMER"],
  "profile": {
    "first_name": "João",
    "last_name": "Silva",
    "display_name": "João Silva",
    "avatar_url": "https://cdn.exemplo.com/avatars/joao.jpg",
    "preferred_language": "pt-BR",
    "preferred_currency": "BRL"
  },
  "created_at": "2026-04-18T12:00:00Z"
}
```

> Campos do `profile` podem ser `null` se ainda não configurados pelo usuário.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente, inválido ou expirado |
| `404` | Usuário não encontrado (caso extremo: conta deletada após login) |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("ID confere com environment", () => {
  pm.expect(body.id).to.equal(pm.environment.get("user_id"));
});
pm.test("Email presente", () => pm.expect(body.email).to.be.a("string"));
pm.test("Roles presente", () => pm.expect(body.roles).to.be.an("array").and.not.be.empty);
```

---

## Fluxos de Teste Recomendados

### Fluxo 1: Registro e Primeiro Acesso

Execute as requests na seguinte ordem:

1. **Registrar Usuário** → salva `user_id`
2. **Login** → salva `access_token` e `refresh_token`
3. **Obter Usuário Atual** → valida dados do usuário logado

### Fluxo 2: Ciclo de Vida da Sessão

1. **Login** → obtém tokens iniciais
2. **Renovar Sessão** → rotaciona tokens (simular access token expirado)
3. **Obter Usuário Atual** → confirma que novo token funciona
4. **Logout** → revoga sessão
5. **Obter Usuário Atual** → deve retornar `401`

### Fluxo 3: Verificação de E-mail

1. **Registrar Usuário**
2. Copiar token do e-mail recebido (ou do log do servidor em dev)
3. **Verificar E-mail** com o token copiado
4. **Login** → confirmar que `email_verified: true` no `/v1/users/me`

### Fluxo 4: Login Social (Google)

1. Obter um ID token válido do Google (via SDK do cliente ou `google-auth-library`)
2. **Login Social** `POST /v1/auth/social/google` com o token → salva `access_token`, `refresh_token`, `user_id`
3. **Obter Usuário Atual** `GET /v1/users/me` → confirmar `email_verified: true` e `roles: ["customer"]`
4. Repetir o passo 2 com o mesmo token Google → deve retornar `200 OK` (vínculo já existente, login direto)
5. Testar com `provider` inválido (ex: `/v1/auth/social/facebook`) → deve retornar `400`
6. Testar com token expirado → deve retornar `401`

### Fluxo 5: Recuperação de Senha

1. **Solicitar Redefinição de Senha** com e-mail cadastrado
2. Verificar retorno `200 OK` (body vazio)
3. Testar também com e-mail **não cadastrado** → deve retornar `200 OK` (anti-enumeração)
4. Copiar token do e-mail recebido (ou do log do servidor em dev)
5. **Confirmar Redefinição de Senha** com o token e a nova senha
6. **Login** com a nova senha → confirmar acesso restaurado

---

## Endpoints Disponíveis (Health e Docs)

| Endpoint | Método | Descrição |
|---|---|---|
| `GET /actuator/health` | `GET` | Health check da aplicação |
| `GET /swagger-ui.html` | `GET` | Interface Swagger UI |
| `GET /v3/api-docs` | `GET` | Spec OpenAPI 3.0 em JSON |

---

## Configurações da Aplicação

| Configuração | Valor |
|---|---|
| Porta | `8080` |
| Context path | `/api` |
| JWT access token | Expira em **15 minutos** |
| JWT refresh token | Expira em **7 dias** |
| CORS permitido | `localhost:3000`, `localhost:4200` |
