# Guia Atualizado da API no Postman

Este documento reflete o estado atual do código em `modules/api`, `SecurityConfig` e `application.yaml`.

## 1. O que mudou nesta revisão

- O inventário de endpoints foi atualizado a partir dos controllers reais.
- A exigência de autenticação foi conferida no `SecurityConfig`, não apenas nas anotações Swagger.
- Os paths finais foram corrigidos considerando `server.servlet.context-path: /api`.
- Foram registrados os desvios atuais da API para evitar testes em URLs erradas.

## 2. Observações importantes

### 2.1. Base da aplicação

- Host local: `http://localhost:8080`
- Context path global: `/api`

Isso significa que:

- endpoints com `@RequestMapping("/v1/...")` ficam em `http://localhost:8080/api/v1/...`
- endpoints com `@RequestMapping("/api/v1/...")` ficam em `http://localhost:8080/api/api/v1/...`

Hoje existe essa inconsistência no código.

### 2.2. Inconsistência atual de paths

Os controllers de `auth`, `users`, `profile`, `address` e `2fa` usam `/v1/...`.

Os controllers de `catalog` e `uploads` usam `/api/v1/...`.

Como a aplicação já aplica `/api` no servidor, as rotas finais de catálogo e upload ficam duplicadas com `/api/api/...`.

Exemplos:

- Auth login: `http://localhost:8080/api/v1/auth/login`
- Categories list: `http://localhost:8080/api/api/v1/catalog/categories`
- Upload: `http://localhost:8080/api/api/v1/uploads`

### 2.3. Autenticação real

O `SecurityConfig` libera apenas estas rotas:

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

Todo o restante exige JWT Bearer.

Isso é importante porque alguns controllers de catálogo descrevem endpoints como públicos, mas o `SecurityConfig` atual os protege.

### 2.4. Alertas de segurança já identificados

- Login social Google ainda não valida corretamente `aud`, `iss` e `email_verified`.
- Upload ainda aceita `SVG`, e os arquivos ficam publicamente acessíveis.
- Há segredos default no `application.yaml`.
- O login ainda não aplica lockout/failed-attempt tracking de forma efetiva.

## 3. Configurando o Postman

### 3.1. Environment

Crie um environment chamado `BTree Local` com estas variáveis:

| Variável | Valor inicial | Uso |
|---|---|---|
| `host` | `http://localhost:8080` | host da aplicação |
| `token` | | access token JWT |
| `refreshToken` | | refresh token |
| `transactionId` | | transaction id do fluxo 2FA |
| `setupTokenId` | | setup token do fluxo de ativação de 2FA |
| `categoryId` | | categoria para testes de catálogo |
| `brandId` | | marca para testes de catálogo |
| `productId` | | produto para testes |
| `imageId` | | imagem de produto para testes |
| `addressId` | | endereço para testes |

### 3.2. Authorization

Na collection, configure:

- Type: `Bearer Token`
- Token: `{{token}}`

### 3.3. Script de captura de tokens

Use este script em `login`, `refresh`, `social login` e `2fa verify`:

```javascript
const json = pm.response.json();

if (json.accessToken) pm.environment.set("token", json.accessToken);
if (json.refreshToken) pm.environment.set("refreshToken", json.refreshToken);
if (json.transactionId) pm.environment.set("transactionId", json.transactionId);
if (json.setup_token_id) pm.environment.set("setupTokenId", json.setup_token_id);
if (json.setupTokenId) pm.environment.set("setupTokenId", json.setupTokenId);
```

## 4. Inventário Real de Endpoints

## 4.1. Públicos

| Grupo | Método | URL final |
|---|---|---|
| Auth | `POST` | `{{host}}/api/v1/auth/register` |
| Auth | `POST` | `{{host}}/api/v1/auth/login` |
| Auth | `POST` | `{{host}}/api/v1/auth/verify-email` |
| Auth | `POST` | `{{host}}/api/v1/auth/logout` |
| Auth | `POST` | `{{host}}/api/v1/auth/social/{provider}` |
| Auth | `POST` | `{{host}}/api/v1/auth/password/forgot` |
| Auth | `POST` | `{{host}}/api/v1/auth/refresh` |
| Auth | `POST` | `{{host}}/api/v1/auth/2fa/verify` |

## 4.2. Protegidos por JWT

| Grupo | Método | URL final |
|---|---|---|
| Auth | `POST` | `{{host}}/api/v1/auth/sessions/revoke-all` |
| Users | `GET` | `{{host}}/api/v1/users/me` |
| Profile | `GET` | `{{host}}/api/v1/users/me/profile` |
| Profile | `PUT` | `{{host}}/api/v1/users/me/profile` |
| 2FA | `POST` | `{{host}}/api/v1/users/me/2fa/setup` |
| 2FA | `POST` | `{{host}}/api/v1/users/me/2fa/enable` |
| Address | `GET` | `{{host}}/api/v1/users/me/addresses` |
| Address | `POST` | `{{host}}/api/v1/users/me/addresses` |
| Address | `PUT` | `{{host}}/api/v1/users/me/addresses/{id}` |
| Address | `DELETE` | `{{host}}/api/v1/users/me/addresses/{id}` |
| Address | `PATCH` | `{{host}}/api/v1/users/me/addresses/{id}/default` |
| Categories | `GET` | `{{host}}/api/api/v1/catalog/categories` |
| Categories | `POST` | `{{host}}/api/api/v1/catalog/categories` |
| Categories | `GET` | `{{host}}/api/api/v1/catalog/categories/{id}` |
| Categories | `PUT` | `{{host}}/api/api/v1/catalog/categories/{id}` |
| Categories | `GET` | `{{host}}/api/api/v1/catalog/categories/{categoryId}/products` |
| Brands | `POST` | `{{host}}/api/api/v1/catalog/brands` |
| Brands | `PUT` | `{{host}}/api/api/v1/catalog/brands/{id}` |
| Brands | `GET` | `{{host}}/api/api/v1/catalog/brands/{brandId}/products` |
| Products | `GET` | `{{host}}/api/api/v1/catalog/products` |
| Products | `GET` | `{{host}}/api/api/v1/catalog/products/featured` |
| Products | `GET` | `{{host}}/api/api/v1/catalog/products/{id}` |
| Products | `POST` | `{{host}}/api/api/v1/catalog/products` |
| Products | `PATCH` | `{{host}}/api/api/v1/catalog/products/{id}` |
| Products | `POST` | `{{host}}/api/api/v1/catalog/products/{id}/publish` |
| Products | `POST` | `{{host}}/api/api/v1/catalog/products/{id}/pause` |
| Products | `POST` | `{{host}}/api/api/v1/catalog/products/{id}/archive` |
| Products | `POST` | `{{host}}/api/api/v1/catalog/products/{productId}/stock/adjustments` |
| Products | `POST` | `{{host}}/api/api/v1/catalog/products/{productId}/stock/reservations` |
| Products | `POST` | `{{host}}/api/api/v1/catalog/products/{productId}/images` |
| Products | `DELETE` | `{{host}}/api/api/v1/catalog/products/{productId}/images/{imageId}` |
| Products | `PATCH` | `{{host}}/api/api/v1/catalog/products/{productId}/images/{imageId}/primary` |
| Products | `PUT` | `{{host}}/api/api/v1/catalog/products/{productId}/images/reorder` |
| Uploads | `POST` | `{{host}}/api/api/v1/uploads` |

## 5. Payloads úteis para teste

## 5.1. Auth

### Registrar usuário

`POST {{host}}/api/v1/auth/register`

```json
{
  "username": "joao.silva",
  "email": "joao.silva@email.com",
  "password": "Senha@123"
}
```

### Login

`POST {{host}}/api/v1/auth/login`

```json
{
  "identifier": "joao.silva@email.com",
  "password": "Senha@123"
}
```

### Refresh

`POST {{host}}/api/v1/auth/refresh`

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

### Logout

`POST {{host}}/api/v1/auth/logout`

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

### Social login

`POST {{host}}/api/v1/auth/social/google`

```json
{
  "token": "GOOGLE_ID_TOKEN"
}
```

### Verificar 2FA

`POST {{host}}/api/v1/auth/2fa/verify`

```json
{
  "transactionId": "{{transactionId}}",
  "code": "123456"
}
```

## 5.2. Profile

### Atualizar perfil

`PUT {{host}}/api/v1/users/me/profile`

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

## 5.3. Address

### Criar endereço

`POST {{host}}/api/v1/users/me/addresses`

```json
{
  "label": "Casa",
  "recipient_name": "Joao Silva",
  "street": "Rua Exemplo",
  "number": "123",
  "complement": "Apto 45",
  "neighborhood": "Centro",
  "city": "Sao Paulo",
  "state": "SP",
  "postal_code": "01001-000",
  "country": "BR",
  "is_billing_address": false
}
```

### Atualizar endereço

`PUT {{host}}/api/v1/users/me/addresses/{{addressId}}`

```json
{
  "label": "Trabalho",
  "recipient_name": "Joao Silva",
  "street": "Avenida Nova",
  "number": "500",
  "complement": null,
  "neighborhood": "Bela Vista",
  "city": "Sao Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_billing_address": true
}
```

## 5.4. 2FA

### Iniciar setup

`POST {{host}}/api/v1/users/me/2fa/setup`

Sem body.

### Confirmar setup

`POST {{host}}/api/v1/users/me/2fa/enable`

```json
{
  "setup_token_id": "{{setupTokenId}}",
  "code": "123456"
}
```

## 5.5. Categories

### Criar categoria

`POST {{host}}/api/api/v1/catalog/categories`

```json
{
  "parentId": null,
  "name": "Eletronicos",
  "slug": "eletronicos",
  "description": "Categoria principal",
  "imageUrl": null,
  "sortOrder": 1
}
```

## 5.6. Brands

### Criar marca

`POST {{host}}/api/api/v1/catalog/brands`

```json
{
  "name": "Acme",
  "slug": "acme",
  "description": "Marca de teste",
  "logoUrl": null
}
```

## 5.7. Products

### Buscar produtos

`GET {{host}}/api/api/v1/catalog/products?q=phone&page=0&size=20`

### Criar produto

`POST {{host}}/api/api/v1/catalog/products`

```json
{
  "categoryId": "{{categoryId}}",
  "brandId": "{{brandId}}",
  "name": "Smartphone X",
  "slug": "smartphone-x",
  "description": "Produto de teste",
  "shortDescription": "Resumo",
  "sku": "SKU-001",
  "price": 1999.90,
  "compareAtPrice": 2299.90,
  "costPrice": 1500.00,
  "lowStockThreshold": 5,
  "weight": 0.4,
  "width": 8.0,
  "height": 16.0,
  "depth": 0.8,
  "images": []
}
```

### Atualizar produto

`PATCH {{host}}/api/api/v1/catalog/products/{{productId}}`

```json
{
  "categoryId": "{{categoryId}}",
  "brandId": "{{brandId}}",
  "name": "Smartphone X Pro",
  "slug": "smartphone-x-pro",
  "description": "Descricao atualizada",
  "shortDescription": "Resumo atualizado",
  "sku": "SKU-001",
  "price": 2099.90,
  "compareAtPrice": 2399.90,
  "costPrice": 1600.00,
  "lowStockThreshold": 4,
  "weight": 0.42,
  "width": 8.1,
  "height": 16.1,
  "depth": 0.8,
  "featured": true
}
```

### Ajuste de estoque

`POST {{host}}/api/api/v1/catalog/products/{{productId}}/stock/adjustments`

```json
{
  "delta": 10,
  "movementType": "MANUAL_IN",
  "notes": "Carga inicial",
  "referenceId": null,
  "referenceType": null
}
```

### Reserva de estoque

`POST {{host}}/api/api/v1/catalog/products/{{productId}}/stock/reservations`

```json
{
  "quantity": 2,
  "orderId": null,
  "ttlMinutes": 15
}
```

### Adicionar imagem ao produto

`POST {{host}}/api/api/v1/catalog/products/{{productId}}/images`

```json
{
  "url": "https://cdn.exemplo.com/produto.jpg",
  "altText": "Imagem frontal",
  "sortOrder": 1,
  "primary": true
}
```

### Reordenar imagens

`PUT {{host}}/api/api/v1/catalog/products/{{productId}}/images/reorder`

```json
{
  "imageIds": ["{{imageId}}"]
}
```

## 5.8. Upload

### Upload de imagem

`POST {{host}}/api/api/v1/uploads`

- Body: `form-data`
- Campo: `file`
- Tipos aceitos no código atual: `jpeg`, `png`, `webp`, `gif`, `svg`

Observação:

- `svg` ainda é aceito no código atual, mas isso é um risco de segurança e não deveria ser usado em produção.

## 6. Ordem sugerida de teste manual

1. Registrar usuário
2. Login
3. `GET /api/v1/users/me`
4. Setup 2FA
5. Enable 2FA
6. Login novamente para capturar `transactionId`
7. Verificar 2FA
8. Criar endereço
9. Criar categoria
10. Criar marca
11. Criar produto
12. Ajustar estoque
13. Publicar produto
14. Buscar produto
15. Fazer upload
16. Adicionar imagem ao produto

## 7. Resumo da análise

O projeto hoje expõe 35 endpoints de negócio em `modules/api`.

Principais desvios encontrados:

- catálogo e upload estão com path final duplicado em `/api/api/...`
- alguns endpoints descritos como públicos no controller estão protegidos na prática pelo `SecurityConfig`
- `docs/postman.md` antigo não refletia o estado real do código

Antes de compartilhar a collection com time ou QA, vale corrigir a inconsistência de paths no código para evitar documentação “especial” só para o ambiente atual.
