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
| `transaction_id` | _(vazio)_ | Preenchido automaticamente quando login detecta 2FA ativo |
| `setup_token_id` | _(vazio)_ | Preenchido automaticamente durante configuração de 2FA |
| `address_id` | _(vazio)_ | Preenchido após cadastrar ou listar endereços |
| `brand_id` | _(vazio)_ | Preenchido após criar ou listar marcas |
| `category_id` | _(vazio)_ | Preenchido após criar ou listar categorias |
| `product_id` | _(vazio)_ | Preenchido após criar ou listar produtos |

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
  "userId": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joao_silva",
  "email": "joao@exemplo.com",
  "requiresTwoFactor": true,
  "transactionId": "019600a1-ffff-7d4e-a5f6-abcdef012345"
}
```

> Os campos `accessToken`, `refreshToken` e `accessTokenExpiresAt` são omitidos quando 2FA está ativo (`@JsonInclude(NON_NULL)`). Use o `transactionId` para chamar `POST /v1/auth/2fa/verify`.

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
} else if (body.requiresTwoFactor) {
  pm.environment.set("transaction_id", body.transactionId);
  pm.environment.set("user_id", body.userId);
  pm.test("transactionId salvo para 2FA", () => {
    pm.expect(pm.environment.get("transaction_id")).to.not.be.empty;
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

### 9. Iniciar Configuração de 2FA

**`POST /v1/auth/2fa/setup`**

Gera um secret TOTP e retorna a URI do QR Code para configuração no app autenticador. O secret fica válido por **15 minutos** — use `/2fa/enable` neste prazo.

> **Requer autenticação:** `Authorization: Bearer {{access_token}}`

**Request:**

```http
POST {{base_url}}/v1/auth/2fa/setup
Authorization: Bearer {{access_token}}
```

Body vazio.

**Response `200 OK`:**

```json
{
  "setup_token_id": "019600a1-aaaa-7d4e-a5f6-111111111111",
  "secret": "JBSWY3DPEHPK3PXP",
  "qr_code_uri": "otpauth://totp/BTree:joao@exemplo.com?secret=JBSWY3DPEHPK3PXP&issuer=BTree&algorithm=SHA1&digits=6&period=30"
}
```

| Campo | Descrição |
|---|---|
| `setup_token_id` | ID do token de setup — necessário para `/2fa/enable` |
| `secret` | Secret Base32 para entrada manual no app autenticador |
| `qr_code_uri` | URI `otpauth://` — codifique em QR Code para escaneamento |

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token JWT ausente ou inválido |
| `409` | 2FA já está ativado para este usuário |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("setup_token_id presente", () => {
  pm.expect(body.setup_token_id).to.be.a("string").and.not.be.empty;
  pm.environment.set("setup_token_id", body.setup_token_id);
});
pm.test("secret presente", () => pm.expect(body.secret).to.be.a("string").and.not.be.empty);
pm.test("qr_code_uri começa com otpauth://", () => pm.expect(body.qr_code_uri).to.include("otpauth://totp/"));
```

---

### 10. Confirmar Ativação de 2FA

**`POST /v1/auth/2fa/enable`**

Valida o código TOTP gerado pelo app autenticador e ativa o 2FA permanentemente na conta.

> **Requer autenticação:** `Authorization: Bearer {{access_token}}`  
> **Pré-requisito:** Chamada prévia a `/2fa/setup` nos últimos 15 minutos.

**Request:**

```http
POST {{base_url}}/v1/auth/2fa/enable
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "setup_token_id": "{{setup_token_id}}",
  "code": "123456"
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `setup_token_id` | Sim | UUID do token retornado por `/2fa/setup` |
| `code` | Sim | Exatamente 6 dígitos numéricos |

**Response `204 No Content`:** Body vazio.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | `setup_token_id` ou `code` ausentes / formato inválido |
| `401` | Token JWT ausente ou inválido |
| `422` | Token de setup expirado, já utilizado, tipo incorreto ou código TOTP inválido |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 204", () => pm.response.to.have.status(204));
pm.test("Body vazio", () => pm.expect(pm.response.text()).to.be.empty);
pm.environment.unset("setup_token_id");
```

---

### 11. Verificar Código 2FA (segunda etapa do login)

**`POST /v1/auth/2fa/verify`**

Segunda etapa do login quando 2FA está ativo. Recebe o `transactionId` obtido no login e o código TOTP do app autenticador. Retorna os tokens finais de acesso.

> **Endpoint público** — não requer JWT. O `transactionId` é o mecanismo de autorização desta etapa.  
> O token de transação expira em **5 minutos** após o login.

**Request:**

```http
POST {{base_url}}/v1/auth/2fa/verify
Content-Type: application/json
```

```json
{
  "transactionId": "{{transaction_id}}",
  "code": "123456"
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `transactionId` | Sim | UUID retornado pelo login quando `requiresTwoFactor: true` |
| `code` | Sim | Exatamente 6 dígitos numéricos |

**Response `200 OK`:**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "a9c3b2f1e8d7c6b5a4f3e2d1c0b9a8f7...",
  "accessTokenExpiresAt": "2026-04-18T12:20:00Z",
  "userId": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "username": "joao_silva",
  "email": "joao@exemplo.com"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | `transactionId` ou `code` ausentes / formato inválido |
| `401` | Código TOTP inválido, `transactionId` não encontrado, expirado ou já utilizado; conta bloqueada |
| `403` | Conta desativada |

> **Proteção brute-force:** Após **5 códigos errados** a conta é bloqueada por **15 minutos**. O bloqueio compartilha o mesmo contador do login (tentativas de senha + TOTP somadas).

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("accessToken presente", () => {
  pm.expect(body.accessToken).to.be.a("string").and.not.be.empty;
  pm.environment.set("access_token", body.accessToken);
});
pm.test("refreshToken presente", () => {
  pm.expect(body.refreshToken).to.be.a("string").and.not.be.empty;
  pm.environment.set("refresh_token", body.refreshToken);
});
pm.test("userId confere", () => {
  pm.expect(body.userId).to.equal(pm.environment.get("user_id"));
});
pm.environment.unset("transaction_id");
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

### 2. Obter Perfil Completo

**`GET /v1/users`**

Retorna o perfil detalhado do usuário autenticado (dados cadastrais, preferências, datas de aceite de termos).

**Request:**

```http
GET {{base_url}}/v1/users
Authorization: Bearer {{access_token}}
```

**Response `200 OK`:**

```json
{
  "id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "user_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "first_name": "João",
  "last_name": "Silva",
  "display_name": "João Silva",
  "avatar_url": "https://cdn.exemplo.com/avatars/joao.jpg",
  "birth_date": "1990-05-20",
  "gender": "male",
  "cpf": "123.456.789-00",
  "preferred_language": "pt-BR",
  "preferred_currency": "BRL",
  "newsletter_subscribed": true,
  "accepted_terms_at": "2026-04-18T12:00:00Z",
  "accepted_privacy_at": "2026-04-18T12:00:00Z",
  "created_at": "2026-04-18T12:00:00Z",
  "updated_at": "2026-04-18T14:30:00Z"
}
```

> Campos opcionais (`cpf`, `birth_date`, `gender`, `avatar_url`, `accepted_terms_at`, `accepted_privacy_at`) são omitidos do JSON quando `null` (`@JsonInclude(NON_NULL)`).

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente, inválido ou expirado |
| `422` | Usuário não encontrado (conta removida após login) |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("user_id presente", () => pm.expect(body.user_id).to.be.a("string"));
pm.test("created_at presente", () => pm.expect(body.created_at).to.be.a("string"));
```

---

### 3. Atualizar Perfil

**`PUT /v1/users/me/profile`**

Atualiza os dados de perfil do usuário autenticado. Todos os campos são opcionais — campos ausentes gravam `null` (substituição completa do perfil).

**Request:**

```http
PUT {{base_url}}/v1/users/me/profile
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "first_name": "João",
  "last_name": "Silva",
  "cpf": "123.456.789-00",
  "birth_date": "1990-05-20",
  "gender": "male",
  "preferred_language": "pt-BR",
  "preferred_currency": "BRL",
  "newsletter_subscribed": true
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `first_name` | Não | Máximo 100 caracteres |
| `last_name` | Não | Máximo 100 caracteres |
| `cpf` | Não | Formato `XXX.XXX.XXX-XX` |
| `birth_date` | Não | Data no formato ISO (`YYYY-MM-DD`) |
| `gender` | Não | Máximo 20 caracteres |
| `preferred_language` | Não | 2–10 caracteres (ex: `pt-BR`, `en`) |
| `preferred_currency` | Não | Exatamente 3 caracteres (ex: `BRL`, `USD`) |
| `newsletter_subscribed` | Não | `true` ou `false` |

**Response `200 OK`:**

```json
{
  "id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "user_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "first_name": "João",
  "last_name": "Silva",
  "display_name": "João Silva",
  "avatar_url": null,
  "birth_date": "1990-05-20",
  "gender": "male",
  "cpf": "123.456.789-00",
  "preferred_language": "pt-BR",
  "preferred_currency": "BRL",
  "newsletter_subscribed": true,
  "updated_at": "2026-04-19T10:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Formato inválido (ex: CPF fora do padrão, `preferred_currency` com != 3 chars) |
| `401` | Token ausente, inválido ou expirado |
| `409` | Conflito de versão (optimistic locking — outro request modificou o perfil simultaneamente) |
| `422` | Usuário não encontrado ou regra de negócio violada |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("updated_at presente", () => pm.expect(body.updated_at).to.be.a("string"));
pm.test("first_name confere", () => pm.expect(body.first_name).to.equal("João"));
```

---

## Contexto: Addresses

**Base path:** `/v1/users/me/addresses`  
**Segurança:** Todos os endpoints requerem **JWT válido** no header `Authorization: Bearer {{access_token}}`.

---

### 1. Cadastrar Endereço

**`POST /v1/users/me/addresses`**

Adiciona um novo endereço ao cadastro do usuário autenticado. O primeiro endereço cadastrado é automaticamente marcado como padrão (`is_default: true`).

**Request:**

```http
POST {{base_url}}/v1/users/me/addresses
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "label": "Casa",
  "recipient_name": "João Silva",
  "street": "Rua das Flores",
  "number": "123",
  "complement": "Apto 45",
  "neighborhood": "Centro",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_billing_address": false
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `label` | Não | Máximo 50 caracteres |
| `recipient_name` | Não | Máximo 150 caracteres |
| `street` | **Sim** | Máximo 255 caracteres |
| `number` | Não | Máximo 20 caracteres |
| `complement` | Não | Máximo 100 caracteres |
| `neighborhood` | Não | Máximo 100 caracteres |
| `city` | **Sim** | Máximo 100 caracteres |
| `state` | **Sim** | Exatamente 2 letras maiúsculas (ex: `SP`, `RJ`) |
| `postal_code` | **Sim** | Formato `XXXXX-XXX` ou `XXXXXXXX` |
| `country` | Não | Exatamente 2 caracteres; padrão `BR` se omitido |
| `is_billing_address` | Não | `true` ou `false`; padrão `false` |

**Response `201 Created`:**

```json
{
  "id": "019600a1-1111-7d4e-a5f6-aaaaaaaaaaaa",
  "user_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "label": "Casa",
  "recipient_name": "João Silva",
  "street": "Rua das Flores",
  "number": "123",
  "complement": "Apto 45",
  "neighborhood": "Centro",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_default": true,
  "is_billing_address": false,
  "created_at": "2026-04-19T10:00:00Z"
}
```

> Campos `null` são omitidos do JSON (`@JsonInclude(NON_NULL)`). O campo `is_default` é `true` se for o primeiro endereço ativo do usuário.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente, `state` inválido ou `postal_code` fora do formato |
| `401` | Token ausente, inválido ou expirado |
| `422` | Regra de negócio violada |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 201", () => pm.response.to.have.status(201));
const body = pm.response.json();
pm.test("id presente", () => {
  pm.expect(body.id).to.be.a("string").and.not.be.empty;
  pm.environment.set("address_id", body.id);
});
pm.test("street confere", () => pm.expect(body.street).to.equal("Rua das Flores"));
pm.test("country padrão BR", () => pm.expect(body.country).to.equal("BR"));
```

---

### 2. Listar Endereços

**`GET /v1/users/me/addresses`**

Retorna todos os endereços ativos (não removidos) do usuário autenticado.

**Request:**

```http
GET {{base_url}}/v1/users/me/addresses
Authorization: Bearer {{access_token}}
```

**Response `200 OK`:**

```json
{
  "items": [
    {
      "id": "019600a1-1111-7d4e-a5f6-aaaaaaaaaaaa",
      "label": "Casa",
      "recipientName": "João Silva",
      "street": "Rua das Flores",
      "number": "123",
      "complement": "Apto 45",
      "neighborhood": "Centro",
      "city": "São Paulo",
      "state": "SP",
      "postalCode": "01310-100",
      "country": "BR",
      "isDefault": true,
      "isBillingAddress": false,
      "createdAt": "2026-04-19T10:00:00Z",
      "updatedAt": "2026-04-19T10:00:00Z"
    }
  ]
}
```

> Os campos da lista usam **camelCase** (sem `@JsonProperty`). Campos `null` (ex: `complement`, `label`) aparecem como `null` no JSON.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente, inválido ou expirado |
| `422` | Usuário não encontrado |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("items é array", () => pm.expect(body.items).to.be.an("array"));
if (body.items.length > 0) {
  pm.environment.set("address_id", body.items[0].id);
  pm.test("primeiro item tem id", () => pm.expect(body.items[0].id).to.be.a("string"));
}
```

---

### 3. Editar Endereço

**`PUT /v1/users/me/addresses/{id}`**

Atualiza todos os dados de um endereço existente. Substituição completa — campos ausentes são gravados como `null`. O campo `is_default` **não** é alterável por este endpoint.

**Request:**

```http
PUT {{base_url}}/v1/users/me/addresses/{{address_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "label": "Trabalho",
  "recipient_name": "João Silva",
  "street": "Avenida Paulista",
  "number": "1000",
  "complement": "Sala 201",
  "neighborhood": "Bela Vista",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_billing_address": true
}
```

**Validações:** Idênticas ao `POST /v1/users/me/addresses` (ver tabela acima).

**Response `200 OK`:**

```json
{
  "id": "019600a1-1111-7d4e-a5f6-aaaaaaaaaaaa",
  "user_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "label": "Trabalho",
  "recipient_name": "João Silva",
  "street": "Avenida Paulista",
  "number": "1000",
  "complement": "Sala 201",
  "neighborhood": "Bela Vista",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_default": true,
  "is_billing_address": true,
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T11:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente, `state` inválido ou `postal_code` fora do formato |
| `401` | Token ausente, inválido ou expirado |
| `422` | Endereço não encontrado, já removido ou pertence a outro usuário |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("id confere", () => pm.expect(body.id).to.equal(pm.environment.get("address_id")));
pm.test("updated_at presente", () => pm.expect(body.updated_at).to.be.a("string"));
pm.test("label atualizado", () => pm.expect(body.label).to.equal("Trabalho"));
```

---

### 4. Remover Endereço

**`DELETE /v1/users/me/addresses/{id}`**

Aplica soft delete em um endereço. O registro é preservado no banco para manter histórico em pedidos anteriores. Não é possível remover o endereço padrão enquanto houver outros endereços ativos — defina outro como padrão primeiro.

**Request:**

```http
DELETE {{base_url}}/v1/users/me/addresses/{{address_id}}
Authorization: Bearer {{access_token}}
```

**Response `204 No Content`:** Body vazio.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente, inválido ou expirado |
| `422` | Endereço não encontrado, já removido, pertence a outro usuário ou é o endereço padrão com outros endereços ativos |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 204", () => pm.response.to.have.status(204));
pm.test("Body vazio", () => pm.expect(pm.response.text()).to.be.empty);
pm.environment.unset("address_id");
```

---

### 5. Definir Endereço Padrão

**`PATCH /v1/users/me/addresses/{id}/default`**

Marca um endereço como padrão de entrega. Operação atômica e idempotente — se o endereço já for o padrão, retorna o mesmo resultado sem erro.

**Request:**

```http
PATCH {{base_url}}/v1/users/me/addresses/{{address_id}}/default
Authorization: Bearer {{access_token}}
```

Body vazio.

**Response `200 OK`:**

```json
{
  "id": "019600a1-1111-7d4e-a5f6-aaaaaaaaaaaa",
  "user_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "label": "Casa",
  "recipient_name": "João Silva",
  "street": "Rua das Flores",
  "number": "123",
  "complement": "Apto 45",
  "neighborhood": "Centro",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_default": true,
  "is_billing_address": false,
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T11:30:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente, inválido ou expirado |
| `422` | Endereço não encontrado, já removido ou pertence a outro usuário |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("is_default é true", () => pm.expect(body.is_default).to.be.true);
pm.test("id confere", () => pm.expect(body.id).to.equal(pm.environment.get("address_id")));
```

---

---

## Contexto: Catalog — Categories

**Base path:** `/v1/catalog/categories`  
**Segurança:** Todos os endpoints requerem **JWT válido** no header `Authorization: Bearer {{access_token}}`.

---

### 1. Criar Categoria

**`POST /v1/catalog/categories`**

Cria uma nova categoria de produto. Omita `parent_id` para criar categoria raiz. O slug deve ser único e seguir formato kebab-case.

**Request:**

```http
POST {{base_url}}/v1/catalog/categories
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "name": "Calçados",
  "slug": "calcados",
  "description": "Tênis, sapatos e sandálias",
  "image_url": "https://cdn.example.com/calcados.jpg",
  "parent_id": null,
  "sort_order": 1
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `name` | **Sim** | Máximo 200 caracteres |
| `slug` | **Sim** | Máximo 256 caracteres; kebab-case (`^[a-z0-9]+(?:-[a-z0-9]+)*$`) |
| `description` | Não | Texto livre |
| `image_url` | Não | Máximo 512 caracteres |
| `parent_id` | Não | UUID de categoria pai existente; omitir para raiz |
| `sort_order` | Não | Inteiro ≥ 0; padrão `0` |

**Response `201 Created`:**

```json
{
  "id": "019600a1-2222-7d4e-a5f6-bbbbbbbbbbbb",
  "name": "Calçados",
  "slug": "calcados",
  "description": "Tênis, sapatos e sandálias",
  "image_url": "https://cdn.example.com/calcados.jpg",
  "parent_id": null,
  "sort_order": 1,
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T10:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente ou slug inválido |
| `401` | Token ausente ou inválido |
| `422` | Slug já em uso, `parent_id` não encontrado ou removido |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 201", () => pm.response.to.have.status(201));
const body = pm.response.json();
pm.test("id presente", () => {
  pm.expect(body.id).to.be.a("string").and.not.be.empty;
  pm.environment.set("category_id", body.id);
});
pm.test("slug confere", () => pm.expect(body.slug).to.equal("calcados"));
```

---

### 2. Listar Categorias

**`GET /v1/catalog/categories`**

Retorna a árvore completa de categorias ativas (não removidas), com filhos aninhados no campo `children`. Respeita `sort_order ASC` em cada nível. Categorias soft-deletadas são excluídas.

**Request:**

```http
GET {{base_url}}/v1/catalog/categories
Authorization: Bearer {{access_token}}
```

**Response `200 OK`:**

```json
[
  {
    "id": "019600a1-2222-7d4e-a5f6-bbbbbbbbbbbb",
    "name": "Calçados",
    "slug": "calcados",
    "description": "Tênis, sapatos e sandálias",
    "image_url": "https://cdn.example.com/calcados.jpg",
    "parent_id": null,
    "sort_order": 1,
    "children": [
      {
        "id": "019600a1-3333-7d4e-a5f6-cccccccccccc",
        "name": "Tênis",
        "slug": "tenis",
        "description": null,
        "image_url": null,
        "parent_id": "019600a1-2222-7d4e-a5f6-bbbbbbbbbbbb",
        "sort_order": 0,
        "children": []
      }
    ]
  }
]
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente ou inválido |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("Resposta é array", () => pm.expect(body).to.be.an("array"));
if (body.length > 0) {
  pm.environment.set("category_id", body[0].id);
  pm.test("Primeiro item tem children", () => pm.expect(body[0].children).to.be.an("array"));
}
```

---

### 3. Editar Categoria

**`PUT /v1/catalog/categories/{id}`**

Atualiza todos os campos mutáveis de uma categoria existente (PUT semântico — substituição completa). Campos ausentes são gravados como `null`. Não é possível editar categorias soft-deletadas.

**Request:**

```http
PUT {{base_url}}/v1/catalog/categories/{{category_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "name": "Calçados Esportivos",
  "slug": "calcados-esportivos",
  "description": "Tênis e chuteiras",
  "image_url": "https://cdn.example.com/calcados-v2.jpg",
  "parent_id": null,
  "sort_order": 2
}
```

**Validações:** Idênticas ao `POST /v1/catalog/categories`.

**Response `200 OK`:**

```json
{
  "id": "019600a1-2222-7d4e-a5f6-bbbbbbbbbbbb",
  "name": "Calçados Esportivos",
  "slug": "calcados-esportivos",
  "description": "Tênis e chuteiras",
  "image_url": "https://cdn.example.com/calcados-v2.jpg",
  "parent_id": null,
  "sort_order": 2,
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T12:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente ou slug inválido |
| `401` | Token ausente ou inválido |
| `422` | Categoria não encontrada, já deletada, slug em uso por outra categoria, ou `parent_id` inválido |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("id confere", () => pm.expect(body.id).to.equal(pm.environment.get("category_id")));
pm.test("updated_at presente", () => pm.expect(body.updated_at).to.be.a("string"));
pm.test("name atualizado", () => pm.expect(body.name).to.equal("Calçados Esportivos"));
```

---

## Contexto: Catalog — Brands

**Base path:** `/v1/catalog/brands`  
**Segurança:** Todos os endpoints requerem **JWT válido** no header `Authorization: Bearer {{access_token}}`.

---

### 1. Criar Marca

**`POST /v1/catalog/brands`**

Cadastra uma nova marca no catálogo. O slug deve ser único entre marcas ativas.

**Request:**

```http
POST {{base_url}}/v1/catalog/brands
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "name": "Nike",
  "slug": "nike",
  "description": "Marca esportiva americana",
  "logo_url": "https://cdn.example.com/nike.png"
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `name` | **Sim** | Máximo 200 caracteres |
| `slug` | **Sim** | Máximo 256 caracteres; apenas letras minúsculas, números e hífens (`^[a-z0-9]+(?:-[a-z0-9]+)*$`) |
| `description` | Não | Texto livre |
| `logo_url` | Não | Máximo 512 caracteres |

**Response `201 Created`:**

```json
{
  "id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "name": "Nike",
  "slug": "nike",
  "description": "Marca esportiva americana",
  "logo_url": "https://cdn.example.com/nike.png",
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T10:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente ou formato de slug inválido |
| `401` | Token ausente ou inválido |
| `422` | Slug já em uso por outra marca ativa |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 201", () => pm.response.to.have.status(201));
const body = pm.response.json();
pm.test("id presente", () => {
  pm.expect(body.id).to.be.a("string").and.not.be.empty;
  pm.environment.set("brand_id", body.id);
});
pm.test("slug confere", () => pm.expect(body.slug).to.equal("nike"));
pm.test("created_at presente", () => pm.expect(body.created_at).to.be.a("string"));
```

---

### 2. Listar Marcas

**`GET /v1/catalog/brands`**

Retorna todas as marcas ativas (não soft-deletadas) do catálogo, sem paginação.

**Request:**

```http
GET {{base_url}}/v1/catalog/brands
Authorization: Bearer {{access_token}}
```

**Response `200 OK`:**

```json
[
  {
    "id": "019600a1-b2c3-7d4e-a5f6-789012345678",
    "name": "Nike",
    "slug": "nike",
    "description": "Marca esportiva americana",
    "logo_url": "https://cdn.example.com/nike.png",
    "created_at": "2026-04-19T10:00:00Z",
    "updated_at": "2026-04-19T10:00:00Z"
  },
  {
    "id": "019600a1-cccc-7d4e-a5f6-aabbccddeeff",
    "name": "Adidas",
    "slug": "adidas",
    "description": null,
    "logo_url": null,
    "created_at": "2026-04-19T11:00:00Z",
    "updated_at": "2026-04-19T11:00:00Z"
  }
]
```

> Campos `null` são omitidos do JSON (`@JsonInclude(NON_NULL)`). Array vazio `[]` quando não houver marcas cadastradas.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente ou inválido |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("Resposta é array", () => pm.expect(body).to.be.an("array"));
if (body.length > 0) {
  pm.environment.set("brand_id", body[0].id);
  pm.test("Primeiro item tem id e slug", () => {
    pm.expect(body[0].id).to.be.a("string");
    pm.expect(body[0].slug).to.be.a("string");
  });
}
```

---

### 3. Editar Marca

**`PUT /v1/catalog/brands/{id}`**

Atualiza todos os campos mutáveis de uma marca existente (PUT semântico — substituição completa). Não é possível editar marcas soft-deletadas.

**Request:**

```http
PUT {{base_url}}/v1/catalog/brands/{{brand_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "name": "Nike Updated",
  "slug": "nike",
  "description": "Descrição atualizada",
  "logo_url": "https://cdn.example.com/nike-v2.png"
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `name` | **Sim** | Máximo 200 caracteres |
| `slug` | **Sim** | Máximo 256 caracteres; kebab-case (`^[a-z0-9]+(?:-[a-z0-9]+)*$`) |
| `description` | Não | Texto livre |
| `logo_url` | Não | Máximo 512 caracteres |

**Response `200 OK`:**

```json
{
  "id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "name": "Nike Updated",
  "slug": "nike",
  "description": "Descrição atualizada",
  "logo_url": "https://cdn.example.com/nike-v2.png",
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T12:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente ou slug inválido |
| `401` | Token ausente ou inválido |
| `422` | Marca não encontrada, já deletada, ou novo slug em uso por outra marca ativa |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("id confere", () => pm.expect(body.id).to.equal(pm.environment.get("brand_id")));
pm.test("updated_at presente", () => pm.expect(body.updated_at).to.be.a("string"));
pm.test("name atualizado", () => pm.expect(body.name).to.equal("Nike Updated"));
```

---

## Contexto: Catalog — Products

**Base path:** `/v1/catalog/products`  
**Segurança:** Endpoints de escrita requerem **JWT válido**. Endpoints de leitura são públicos.

---

### 1. Criar Produto

**`POST /v1/catalog/products`**

Cadastra um novo produto com status `DRAFT`. Slug e SKU devem ser únicos.

**Request:**

```http
POST {{base_url}}/v1/catalog/products
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "name": "Tênis Air Max",
  "slug": "tenis-air-max",
  "sku": "NK-AM-001",
  "description": "Tênis esportivo com amortecimento Air Max",
  "price": 599.90,
  "original_price": 799.90,
  "brand_id": "{{brand_id}}",
  "category_id": "{{category_id}}",
  "stock_quantity": 50,
  "weight": 0.8,
  "tags": ["esportivo", "corrida"],
  "is_featured": false
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `name` | **Sim** | Máximo 500 caracteres |
| `slug` | **Sim** | Kebab-case; único entre produtos |
| `sku` | **Sim** | Máximo 100 caracteres; único |
| `description` | Não | Texto livre |
| `price` | **Sim** | Decimal > 0 |
| `original_price` | Não | Decimal > 0 |
| `brand_id` | Não | UUID de marca existente |
| `category_id` | Não | UUID de categoria existente |
| `stock_quantity` | Não | Inteiro ≥ 0; padrão `0` |
| `weight` | Não | Decimal > 0 (kg) |
| `tags` | Não | Array de strings |
| `is_featured` | Não | `true` ou `false`; padrão `false` |

**Response `201 Created`:**

```json
{
  "id": "019600a1-4444-7d4e-a5f6-dddddddddddd",
  "name": "Tênis Air Max",
  "slug": "tenis-air-max",
  "sku": "NK-AM-001",
  "description": "Tênis esportivo com amortecimento Air Max",
  "price": 599.90,
  "original_price": 799.90,
  "status": "DRAFT",
  "stock_quantity": 50,
  "brand_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "category_id": "019600a1-2222-7d4e-a5f6-bbbbbbbbbbbb",
  "weight": 0.8,
  "tags": ["esportivo", "corrida"],
  "is_featured": false,
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T10:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Campo obrigatório ausente ou formato inválido |
| `401` | Token ausente ou inválido |
| `422` | Slug ou SKU já em uso; `brand_id`/`category_id` não encontrado |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 201", () => pm.response.to.have.status(201));
const body = pm.response.json();
pm.test("id presente", () => {
  pm.expect(body.id).to.be.a("string").and.not.be.empty;
  pm.environment.set("product_id", body.id);
});
pm.test("status é DRAFT", () => pm.expect(body.status).to.equal("DRAFT"));
pm.test("slug confere", () => pm.expect(body.slug).to.equal("tenis-air-max"));
```

---

### 2. Listar Todos os Produtos

**`GET /v1/catalog/products`**

Retorna lista paginada de todos os produtos independente de status. Endpoint público.

**Request:**

```http
GET {{base_url}}/v1/catalog/products?page=0&size=20
```

**Query Parameters:**

| Parâmetro | Obrigatório | Padrão | Descrição |
|---|---|---|---|
| `page` | Não | `0` | Número da página (zero-based) |
| `size` | Não | `20` | Itens por página |

**Response `200 OK`:**

```json
{
  "items": [
    {
      "id": "019600a1-4444-7d4e-a5f6-dddddddddddd",
      "name": "Tênis Air Max",
      "slug": "tenis-air-max",
      "sku": "NK-AM-001",
      "price": 599.90,
      "status": "DRAFT",
      "stock_quantity": 50,
      "is_featured": false,
      "created_at": "2026-04-19T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1,
  "total_pages": 1
}
```

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("items é array", () => pm.expect(body.items).to.be.an("array"));
pm.test("paginação presente", () => {
  pm.expect(body.page).to.be.a("number");
  pm.expect(body.total).to.be.a("number");
});
if (body.items.length > 0) {
  pm.environment.set("product_id", body.items[0].id);
}
```

---

### 3. Buscar Produto por ID

**`GET /v1/catalog/products/{id}`**

Retorna dados completos de um produto incluindo imagens. Retorna `404` para produtos soft-deletados. Endpoint público.

**Request:**

```http
GET {{base_url}}/v1/catalog/products/{{product_id}}
```

**Response `200 OK`:**

```json
{
  "id": "019600a1-4444-7d4e-a5f6-dddddddddddd",
  "name": "Tênis Air Max",
  "slug": "tenis-air-max",
  "sku": "NK-AM-001",
  "description": "Tênis esportivo com amortecimento Air Max",
  "price": 599.90,
  "original_price": 799.90,
  "status": "DRAFT",
  "stock_quantity": 50,
  "brand_id": "019600a1-b2c3-7d4e-a5f6-789012345678",
  "category_id": "019600a1-2222-7d4e-a5f6-bbbbbbbbbbbb",
  "weight": 0.8,
  "tags": ["esportivo", "corrida"],
  "is_featured": false,
  "images": [],
  "created_at": "2026-04-19T10:00:00Z",
  "updated_at": "2026-04-19T10:00:00Z"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `404` | Produto não encontrado ou soft-deletado |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("id confere", () => pm.expect(body.id).to.equal(pm.environment.get("product_id")));
pm.test("images é array", () => pm.expect(body.images).to.be.an("array"));
```

---

### 4. Listar Produtos por Categoria

**`GET /v1/catalog/products/by-category/{categoryId}`**

Retorna lista paginada de produtos com status `ACTIVE` de uma categoria específica. Endpoint público.

**Request:**

```http
GET {{base_url}}/v1/catalog/products/by-category/{{category_id}}?page=0&size=20
```

**Query Parameters:**

| Parâmetro | Obrigatório | Padrão | Descrição |
|---|---|---|---|
| `page` | Não | `0` | Número da página (zero-based) |
| `size` | Não | `20` | Itens por página |

**Response `200 OK`:** Mesmo formato de `/v1/catalog/products` — somente produtos `ACTIVE`.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `422` | Categoria não encontrada |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("items é array", () => pm.expect(body.items).to.be.an("array"));
body.items.forEach(item => {
  pm.test(`Produto ${item.id} tem status ACTIVE`, () => pm.expect(item.status).to.equal("ACTIVE"));
});
```

---

### 5. Atualizar Produto

**`PATCH /v1/catalog/products/{id}`**

Atualiza os dados cadastrais de um produto. Não altera status nem estoque.

**Request:**

```http
PATCH {{base_url}}/v1/catalog/products/{{product_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "name": "Tênis Air Max Plus",
  "slug": "tenis-air-max-plus",
  "sku": "NK-AM-001",
  "description": "Versão atualizada com tecnologia Plus",
  "price": 649.90,
  "original_price": 849.90,
  "brand_id": "{{brand_id}}",
  "category_id": "{{category_id}}",
  "weight": 0.85,
  "tags": ["esportivo", "corrida", "premium"],
  "is_featured": true
}
```

**Response `200 OK`:** Produto atualizado com `updated_at` novo.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Formato inválido |
| `401` | Token ausente ou inválido |
| `422` | Produto não encontrado, slug/SKU em uso por outro produto |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("id confere", () => pm.expect(body.id).to.equal(pm.environment.get("product_id")));
pm.test("updated_at presente", () => pm.expect(body.updated_at).to.be.a("string"));
```

---

### 6. Ajustar Estoque

**`POST /v1/catalog/products/{productId}/stock/adjustments`**

Registra entrada ou saída manual de estoque. `delta > 0` = entrada, `delta < 0` = saída. Transiciona automaticamente o status `ACTIVE ↔ OUT_OF_STOCK` conforme necessário.

**Request:**

```http
POST {{base_url}}/v1/catalog/products/{{product_id}}/stock/adjustments
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "delta": 10,
  "reason": "PURCHASE",
  "notes": "Recebimento NF 12345"
}
```

**Validações:**

| Campo | Obrigatório | Restrições |
|---|---|---|
| `delta` | **Sim** | Inteiro ≠ 0; negativo para saída |
| `reason` | **Sim** | Enum: `PURCHASE`, `RETURN`, `ADJUSTMENT`, `DAMAGE`, `SALE`, `RESERVATION_RELEASE` |
| `notes` | Não | Texto livre |

**Response `200 OK`:**

```json
{
  "product_id": "019600a1-4444-7d4e-a5f6-dddddddddddd",
  "previous_quantity": 50,
  "new_quantity": 60,
  "delta": 10,
  "reason": "PURCHASE",
  "movement_id": "019600a1-5555-7d4e-a5f6-eeeeeeeeeeee"
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | `delta` = 0 ou `reason` inválido |
| `401` | Token ausente ou inválido |
| `422` | Produto não encontrado; estoque ficaria negativo |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("new_quantity é number", () => pm.expect(body.new_quantity).to.be.a("number"));
pm.test("delta confere", () => pm.expect(body.delta).to.equal(10));
```

---

### 7. Histórico de Movimentações de Estoque

**`GET /v1/catalog/products/{productId}/stock/movements`**

Retorna o histórico paginado de movimentações de estoque do produto, ordenado do mais recente para o mais antigo.

**Request:**

```http
GET {{base_url}}/v1/catalog/products/{{product_id}}/stock/movements?page=0&size=20
Authorization: Bearer {{access_token}}
```

**Query Parameters:**

| Parâmetro | Obrigatório | Padrão | Descrição |
|---|---|---|---|
| `page` | Não | `0` | Número da página (zero-based) |
| `size` | Não | `20` | Itens por página |

**Response `200 OK`:**

```json
{
  "items": [
    {
      "id": "019600a1-5555-7d4e-a5f6-eeeeeeeeeeee",
      "product_id": "019600a1-4444-7d4e-a5f6-dddddddddddd",
      "delta": 10,
      "quantity_before": 50,
      "quantity_after": 60,
      "reason": "PURCHASE",
      "notes": "Recebimento NF 12345",
      "created_at": "2026-04-19T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1,
  "total_pages": 1
}
```

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `401` | Token ausente ou inválido |
| `422` | Produto não encontrado |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("items é array", () => pm.expect(body.items).to.be.an("array"));
pm.test("paginação presente", () => pm.expect(body.total).to.be.a("number"));
```

---

## Contexto: Media

**Base path:** `/v1/media`  
**Segurança:** Todos os endpoints requerem **JWT válido** no header `Authorization: Bearer {{access_token}}`.

---

### 1. Upload de Imagem

**`POST /v1/media/upload`**

Armazena uma imagem no object storage (MinIO/S3) e retorna a URL pública de acesso direto.

**Request:**

```http
POST {{base_url}}/v1/media/upload
Authorization: Bearer {{access_token}}
Content-Type: multipart/form-data
```

| Campo | Tipo | Descrição |
|---|---|---|
| `file` | `File` | Imagem a ser enviada. Tamanho máximo: **10 MB**. |

> No Postman: aba **Body → form-data → Key = `file`, Type = `File`**, selecionar o arquivo.

**Formatos aceitos:**

| MIME Type | Extensão |
|---|---|
| `image/jpeg` | `.jpg` / `.jpeg` |
| `image/png` | `.png` |
| `image/webp` | `.webp` |
| `image/gif` | `.gif` |
| `image/svg+xml` | `.svg` |

**Response `200 OK`:**

```json
{
  "url": "http://localhost:9000/btree-uploads/a3f7c1d9-e8b2-4f50-9c6d-0123456789ab.jpg"
}
```

> A URL retornada é pública e pode ser referenciada diretamente em `logo_url` de marcas, `avatar_url` de perfis ou qualquer outro campo de imagem do domínio.

**Cenários de Erro:**

| Status | Causa |
|---|---|
| `400` | Arquivo vazio ou tipo MIME não suportado |
| `401` | Token ausente ou inválido |
| `422` | Falha no armazenamento (MinIO indisponível, disco cheio, etc.) |

**Scripts de Teste (Tests tab):**

```javascript
pm.test("Status 200", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.test("url presente e começa com http", () => {
  pm.expect(body.url).to.be.a("string").and.not.be.empty;
  pm.expect(body.url).to.match(/^https?:\/\//);
  pm.environment.set("uploaded_url", body.url);
});
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

### Fluxo 6: Ativar 2FA

> Pré-requisito: usuário registrado e com sessão ativa (`access_token` no environment).

1. **Iniciar Configuração 2FA** `POST /v1/auth/2fa/setup`
   - Salva `setup_token_id` no environment
   - Abre o `qr_code_uri` em um gerador de QR Code (ex: `qr-code-generator.com`) ou insere o `secret` manualmente no app autenticador (Google Authenticator, Authy, etc.)
2. No app autenticador, aguarda o código de 6 dígitos aparecer
3. **Confirmar Ativação 2FA** `POST /v1/auth/2fa/enable`
   - Body: `{ "setup_token_id": "{{setup_token_id}}", "code": "<código do app>" }`
   - Deve retornar `204 No Content`
4. **Login** `POST /v1/auth/login` → deve retornar `requiresTwoFactor: true` e `transactionId` (sem tokens)
5. Testar nova chamada ao `/2fa/setup` → deve retornar `409 Conflict` (2FA já ativo)

### Fluxo 7: Atualizar Perfil do Usuário

> Pré-requisito: sessão ativa (`access_token` no environment).

1. **Obter Perfil Completo** `GET /v1/users` → verificar campos atuais
2. **Atualizar Perfil** `PUT /v1/users/me/profile` com dados completos → deve retornar `200 OK` com `updated_at` novo
3. **Obter Perfil Completo** `GET /v1/users` → confirmar que os campos foram atualizados
4. Testar payload com `cpf` em formato inválido (ex: `12345678900` sem pontuação) → deve retornar `400`
5. Testar payload com `preferred_currency` de 4 chars (ex: `USDT`) → deve retornar `400`

### Fluxo 8: Gerenciamento de Endereços (CRUD completo)

> Pré-requisito: sessão ativa (`access_token` no environment).

1. **Listar Endereços** `GET /v1/users/me/addresses` → array vazio (nenhum endereço ainda)
2. **Cadastrar Endereço** `POST /v1/users/me/addresses` com dados válidos
   - Deve retornar `201 Created` com `is_default: true` (primeiro endereço)
   - Script salva `address_id` no environment
3. **Listar Endereços** `GET /v1/users/me/addresses` → 1 item, `isDefault: true`
4. **Cadastrar segundo Endereço** → deve retornar `is_default: false`
5. **Listar Endereços** → 2 itens
6. **Editar Endereço** `PUT /v1/users/me/addresses/{{address_id}}` com novos dados → `200 OK`, `updated_at` diferente de `created_at`
7. **Remover Endereço padrão** `DELETE /v1/users/me/addresses/{{address_id_do_padrao}}` enquanto o segundo existe → deve retornar `422` (CANNOT_DELETE_DEFAULT_ADDRESS)
8. **Remover o segundo Endereço** (não padrão) → deve retornar `204 No Content`
9. **Listar Endereços** → 1 item (só o padrão restou)
10. **Remover o Endereço padrão** (único ativo) → deve retornar `204 No Content` (permitido quando é o único)
11. **Listar Endereços** → array vazio

### Fluxo 10: CRUD de Marcas

> Pré-requisito: sessão ativa (`access_token` no environment).

1. **Listar Marcas** `GET /v1/catalog/brands` → array vazio ou existente
2. **Criar Marca** `POST /v1/catalog/brands` com `name`, `slug`, `description`, `logo_url`
   - Deve retornar `201 Created`
   - Script salva `brand_id` no environment
3. **Listar Marcas** `GET /v1/catalog/brands` → confirmar novo item na lista
4. **Editar Marca** `PUT /v1/catalog/brands/{{brand_id}}` com dados alterados → `200 OK`, `updated_at` diferente de `created_at`
5. Testar slug duplicado → `POST` com mesmo `slug` de marca existente → deve retornar `422`
6. Testar slug inválido → `PUT` com `slug: "Minha Marca"` (maiúsculo com espaço) → deve retornar `400`
7. Testar `PUT` em UUID inexistente → deve retornar `422`

### Fluxo 11: Upload de Imagem e Atualização de Logo

> Pré-requisito: sessão ativa e marca criada (`brand_id` no environment).

1. **Upload de Imagem** `POST /v1/media/upload` com um arquivo `.jpg` ou `.png`
   - Deve retornar `200 OK` com `url` pública
   - Script salva `uploaded_url` no environment
2. **Editar Marca** `PUT /v1/catalog/brands/{{brand_id}}` enviando a URL retornada no campo `logo_url`
   - Confirmar que `logo_url` foi atualizado no response
3. **Listar Marcas** `GET /v1/catalog/brands` → confirmar `logo_url` preenchido na marca
4. Testar upload com arquivo `.pdf` → deve retornar `400` (tipo não suportado)
5. Testar upload sem arquivo (campo `file` vazio) → deve retornar `400`

### Fluxo 12: CRUD de Categorias

> Pré-requisito: sessão ativa (`access_token` no environment).

1. **Listar Categorias** `GET /v1/catalog/categories` → array vazio ou existente
2. **Criar Categoria raiz** `POST /v1/catalog/categories` com `name`, `slug`, sem `parent_id`
   - Deve retornar `201 Created`; script salva `category_id`
3. **Criar Subcategoria** `POST /v1/catalog/categories` com `parent_id: "{{category_id}}"`
   - Deve retornar `201 Created` com `parent_id` preenchido
4. **Listar Categorias** → raiz com subcategoria no campo `children`
5. **Editar Categoria** `PUT /v1/catalog/categories/{{category_id}}` → `200 OK`, `updated_at` novo
6. Testar slug duplicado → `POST` com mesmo slug → deve retornar `422`
7. Testar `parent_id` inexistente → deve retornar `422`

---

### Fluxo 13: CRUD de Produtos e Estoque

> Pré-requisito: sessão ativa, `brand_id` e `category_id` no environment.

1. **Criar Produto** `POST /v1/catalog/products`
   - Deve retornar `201 Created` com `status: "DRAFT"`; script salva `product_id`
2. **Buscar Produto por ID** `GET /v1/catalog/products/{{product_id}}` → dados completos com `images: []`
3. **Listar Todos os Produtos** `GET /v1/catalog/products` → produto aparece (qualquer status)
4. **Listar por Categoria** `GET /v1/catalog/products/by-category/{{category_id}}` → array vazio (produto ainda é DRAFT)
5. **Atualizar Produto** `PATCH /v1/catalog/products/{{product_id}}` → `200 OK`, `updated_at` novo
6. **Ajustar Estoque** `POST /v1/catalog/products/{{product_id}}/stock/adjustments`
   - Body: `{ "delta": 10, "reason": "PURCHASE" }` → confirmar `new_quantity = previous + 10`
7. **Histórico de Movimentações** `GET /v1/catalog/products/{{product_id}}/stock/movements` → 1 item
8. Testar saída de estoque maior que disponível → `delta: -999` → deve retornar `422`
9. Testar SKU duplicado → `POST` com mesmo `sku` → deve retornar `422`

---

### Fluxo 9: Login com 2FA Ativo

> Pré-requisito: conta com 2FA ativado (Fluxo 6 concluído).

1. **Login** `POST /v1/auth/login` com credenciais corretas
   - Resposta: `requiresTwoFactor: true`, `transactionId` salvo no environment
   - **Sem** `accessToken` na resposta
2. No app autenticador, obter código TOTP atual (válido por 30 s, tolerância de ±1 período)
3. **Verificar Código 2FA** `POST /v1/auth/2fa/verify`
   - Body: `{ "transactionId": "{{transaction_id}}", "code": "<código do app>" }`
   - Deve retornar `200 OK` com `accessToken`, `refreshToken`
   - Scripts salvam tokens no environment automaticamente
4. **Obter Usuário Atual** `GET /v1/users/me` → confirmar acesso com o novo token
5. **Testar brute-force:** repetir `/2fa/verify` com código errado 5 vezes
   - Após 5 tentativas: próxima chamada deve retornar `401` com mensagem de conta bloqueada
   - Aguardar 15 minutos (ou ajustar `lock_expires_at` no banco em ambiente dev) para desbloquear

---

## Endpoints Disponíveis

### Auth

| Endpoint | Método | Auth | Descrição |
|---|---|---|---|
| `/v1/auth/register` | `POST` | Público | Registrar usuário |
| `/v1/auth/login` | `POST` | Público | Login (retorna tokens ou `transactionId` para 2FA) |
| `/v1/auth/social/{provider}` | `POST` | Público | Login via OAuth2 (Google) |
| `/v1/auth/verify-email` | `POST` | Público | Confirmar e-mail |
| `/v1/auth/refresh` | `POST` | Público | Renovar tokens (token rotation) |
| `/v1/auth/logout` | `POST` | Público | Encerrar sessão |
| `/v1/auth/password/forgot` | `POST` | Público | Solicitar redefinição de senha |
| `/v1/auth/password/reset` | `POST` | Público | Confirmar redefinição de senha |
| `/v1/auth/2fa/setup` | `POST` | Bearer | Iniciar configuração de 2FA |
| `/v1/auth/2fa/enable` | `POST` | Bearer | Ativar 2FA com código TOTP |
| `/v1/auth/2fa/verify` | `POST` | Público | Segunda etapa do login com 2FA |

### Users

| Endpoint | Método | Auth | Descrição |
|---|---|---|---|
| `/v1/users/me` | `GET` | Bearer | Obter usuário atual (dados do JWT) |
| `/v1/users` | `GET` | Bearer | Obter perfil completo |
| `/v1/users/me/profile` | `PUT` | Bearer | Atualizar perfil |
| `/v1/users/me/addresses` | `POST` | Bearer | Cadastrar endereço |
| `/v1/users/me/addresses` | `GET` | Bearer | Listar endereços |
| `/v1/users/me/addresses/{id}` | `PUT` | Bearer | Editar endereço |
| `/v1/users/me/addresses/{id}` | `DELETE` | Bearer | Remover endereço (soft delete) |
| `/v1/users/me/addresses/{id}/default` | `PATCH` | Bearer | Definir endereço padrão (idempotente) |

### Catalog — Categories

| Endpoint | Método | Auth | Descrição |
|---|---|---|---|
| `/v1/catalog/categories` | `POST` | Bearer | Criar categoria |
| `/v1/catalog/categories` | `GET` | Bearer | Listar árvore de categorias ativas |
| `/v1/catalog/categories/{id}` | `PUT` | Bearer | Editar categoria (PUT semântico) |

### Catalog — Brands

| Endpoint | Método | Auth | Descrição |
|---|---|---|---|
| `/v1/catalog/brands` | `POST` | Bearer | Criar marca |
| `/v1/catalog/brands` | `GET` | Bearer | Listar marcas ativas |
| `/v1/catalog/brands/{id}` | `PUT` | Bearer | Editar marca (PUT semântico) |

### Catalog — Products

| Endpoint | Método | Auth | Descrição |
|---|---|---|---|
| `/v1/catalog/products` | `POST` | Bearer | Criar produto (status DRAFT) |
| `/v1/catalog/products` | `GET` | Público | Listar todos os produtos (paginado) |
| `/v1/catalog/products/{id}` | `GET` | Público | Buscar produto por ID |
| `/v1/catalog/products/by-category/{categoryId}` | `GET` | Público | Listar produtos ACTIVE de uma categoria (paginado) |
| `/v1/catalog/products/{id}` | `PATCH` | Bearer | Atualizar dados do produto |
| `/v1/catalog/products/{id}/stock/adjustments` | `POST` | Bearer | Ajustar estoque (entrada/saída manual) |
| `/v1/catalog/products/{id}/stock/movements` | `GET` | Bearer | Histórico de movimentações de estoque (paginado) |

### Media

| Endpoint | Método | Auth | Descrição |
|---|---|---|---|
| `/v1/media/upload` | `POST` | Bearer | Upload de imagem (JPEG, PNG, WebP, GIF, SVG — máx 10 MB) |

### Sistema

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
