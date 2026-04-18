# Task: UC-01 — RegisterUser

## 📋 Resumo

Implementar o registro de um novo usuário no sistema de e-commerce. Este é o **primeiro use case do MVP** — sem ele, não há usuários e nenhuma outra funcionalidade pode operar. O registro cria atomicamente 3 registros: o usuário principal, um perfil vazio e as preferências de notificação com valores padrão.

## 🎯 Objetivo

Permitir que um visitante crie uma conta fornecendo **username, email e senha**. Após o registro:
- O usuário terá uma conta **ativa** porém com **email não verificado**
- Um **perfil inicial** será criado com idioma `pt-BR` e moeda `BRL`
- As **preferências de notificação** serão criadas com os defaults do sistema
- Um **token de verificação de email** será gerado (preparando para o UC-06: VerifyEmail)

## 📦 Contexto Técnico

* **Módulo:** Users
* **Prioridade:** 🔴 CRÍTICO (P0 — MVP Blocker)
* **Tipo:** `[CMD]` — Command (escrita/mutação)
* **Endpoint:** `POST /api/v1/auth/register`
* **Tabelas:** `users.users`, `users.profiles`, `users.notification_preferences`
* **Dependências:** Nenhuma (primeiro use case a ser implementado)

---

## 🏗️ Arquivos a Criar / Alterar

### Core (Use Case)

```
com.btree.users.usecases.auth.register/
  ├── RegisterUserInput.java          — Record com dados de entrada
  ├── RegisterUserOutput.java         — Record com dados de saída
  ├── IRegisterUser.java              — Interface do use case
  └── RegisterUser.java               — Implementação do use case
```

### Persistence (Gateways)

```
com.btree.users.persistence/
  ├── IUserGateway.java               — [ALTERAR] Adicionar métodos de consulta
  ├── IProfileGateway.java            — [NOVO] Interface de persistência de perfil
  └── INotificationPreferenceGateway.java — [NOVO] Interface de persistência de preferências
```

### Infraestrutura

```
com.btree.users.infrastructure.persistence/
  ├── UserGatewayImpl.java            — [NOVO] Implementação JPA/JDBC
  ├── ProfileGatewayImpl.java         — [NOVO] Implementação JPA/JDBC
  └── NotificationPreferenceGatewayImpl.java — [NOVO] Implementação JPA/JDBC

com.btree.users.infrastructure.api/
  └── AuthController.java             — [NOVO] Endpoint POST /api/v1/auth/register

com.btree.users.infrastructure.security/
  └── PasswordHasher.java             — [NOVO] Interface + impl para hash de senha (BCrypt)
```

---

## 📐 Algoritmo — Passo a Passo

### Passo 1: Validação de Entrada

Validar o `RegisterUserInput`:

| Regra | Campo | Condição | Erro |
|-------|-------|----------|------|
| R1 | `username` | não pode ser nulo/vazio | `'username' must not be empty` |
| R2 | `username` | máximo 256 caracteres | `'username' must be at most 256 characters` |
| R3 | `username` | apenas alfanuméricos, `-`, `_` | `'username' contains invalid characters` |
| R4 | `email` | não pode ser nulo/vazio | `'email' must not be empty` |
| R5 | `email` | máximo 256 caracteres | `'email' must be at most 256 characters` |
| R6 | `email` | formato válido (RFC 5322 simplificado) | `'email' format is invalid` |
| R7 | `password` | não pode ser nulo/vazio | `'password' must not be empty` |
| R8 | `password` | mínimo 8 caracteres | `'password' must be at least 8 characters` |
| R9 | `password` | pelo menos 1 maiúscula, 1 minúscula, 1 dígito | `'password' does not meet complexity requirements` |

**Estratégia:** Acumulativa (Notification pattern) — coleta todos os erros antes de responder.

### Passo 2: Regras de Negócio

| Regra | Descrição | Query |
|-------|-----------|-------|
| RN1 | Username deve ser **único** (case-insensitive) | `SELECT 1 FROM users.users WHERE LOWER(username) = LOWER(?)` |
| RN2 | Email deve ser **único** (case-insensitive) | `SELECT 1 FROM users.users WHERE LOWER(email) = LOWER(?)` |

Se violado → retornar erro **409 Conflict** com mensagem específica.

### Passo 3: Processamento

1. **Hash da senha**: Aplicar BCrypt (cost factor 12) no password em texto plano
2. **Criar entidade User**: `User.create(username, email, passwordHash)`
3. **Validar invariantes do User**: `user.validate(notification)` — verifica invariantes do domínio
4. **Criar entidade Profile**: `Profile.create(userId)` — perfil vazio com defaults (pt-BR, BRL)
5. **Criar entidade NotificationPreference**: `NotificationPreference.createDefault(userId)`

### Passo 4: Persistência (Transacional)

> ⚠️ **Tudo dentro de uma única transação.** Se qualquer parte falhar, faz rollback completo.

```
BEGIN TRANSACTION
  1. INSERT em users.users       → User
  2. INSERT em users.profiles    → Profile  
  3. INSERT em users.notification_preferences → NotificationPreference
COMMIT
```

### Passo 5: Retorno

Montar `RegisterUserOutput` com:
- `userId` — UUID do usuário criado
- `username` — username confirmado
- `email` — email confirmado
- `createdAt` — timestamp de criação

**NÃO retornar:** passwordHash, tokens, dados sensíveis.

---

## ⚠️ Casos de Erro

| # | Cenário | Status | Mensagem | Condição |
|---|---------|--------|----------|----------|
| 1 | Campos obrigatórios faltando | 422 | Lista de erros de validação | Qualquer campo obrigatório nulo/vazio |
| 2 | Username já existe | 409 | `Username already exists` | LOWER(username) já no banco |
| 3 | Email já existe | 409 | `Email already exists` | LOWER(email) já no banco |
| 4 | Senha fraca | 422 | `Password does not meet complexity requirements` | Não atende R8/R9 |
| 5 | Erro interno de persistência | 500 | `Internal server error` | Falha no banco/transação |

---

## 🧪 Cenários de Teste

### Unitários

1. ✅ **Sucesso**: username/email/senha válidos → cria User, Profile e NotificationPreference
2. ❌ **Username vazio**: retorna erro de validação
3. ❌ **Email inválido**: retorna erro de validação
4. ❌ **Senha curta** (< 8 chars): retorna erro de validação
5. ❌ **Senha sem complexidade**: retorna erro de validação
6. ❌ **Username duplicado**: retorna 409
7. ❌ **Email duplicado**: retorna 409
8. ✅ **Username case-insensitive**: "Admin" e "admin" são considerados iguais
9. ✅ **Email case-insensitive**: "User@Mail.COM" e "user@mail.com" são iguais
10. ✅ **Profile criado com defaults**: preferredLanguage="pt-BR", preferredCurrency="BRL"
11. ✅ **NotificationPreference com defaults**: email=true, push=true, sms=false
12. ✅ **Senha é hasheada**: passwordHash != password (raw)

### Integração

1. ✅ **Persistência**: User, Profile e Preferences salvos corretamente no banco
2. ✅ **Transação atômica**: falha no Profile → rollback do User
3. ✅ **Índices únicos**: constraint UNIQUE no banco funciona
4. ✅ **Endpoint completo**: POST → 201 Created com body correto

---

## 🔗 Dependências

### Técnicas

* **Banco de dados:** PostgreSQL 17 — schemas `users`
* **Hashing:** BCrypt (Spring Security Crypto ou jBCrypt)
* **Validação:** Notification pattern (já existente no shared)

### Funcionais

* **UC-06 VerifyEmail** — após o registro, será necessário verificar o email (implementar depois)
* **UC-02 AuthenticateUser** — depende de um usuário existente (próximo use case)

---

## 📝 Detalhes de Implementação

### Input (RegisterUserInput)

| Campo | Tipo | Obrigatório | Validação | Exemplo |
|-------|------|:-----------:|-----------|---------|
| `username` | String | ✅ | 1-256 chars, alfanumérico+`-_` | `"johndoe"` |
| `email` | String | ✅ | 1-256 chars, formato email válido | `"john@example.com"` |
| `password` | String | ✅ | min 8 chars, 1 upper, 1 lower, 1 digit | `"S3curePass"` |

### Output (RegisterUserOutput)

| Campo | Tipo | Descrição | Exemplo |
|-------|------|-----------|---------|
| `userId` | String (UUID) | ID do usuário criado | `"01912a4c-..."` |
| `username` | String | Username confirmado | `"johndoe"` |
| `email` | String | Email cadastrado | `"john@example.com"` |
| `createdAt` | String (ISO 8601) | Timestamp de criação | `"2026-04-05T19:30:00Z"` |

---

## 🌐 API

### Request

```
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "S3curePass"
}
```

### Response — 201 Created

```json
{
  "userId": "01912a4c-3f4b-7d8e-9a1b-2c3d4e5f6a7b",
  "username": "johndoe",
  "email": "john@example.com",
  "createdAt": "2026-04-05T19:30:00Z"
}
```

### Response — 422 Unprocessable Entity

```json
{
  "errors": [
    { "message": "'username' must not be empty" },
    { "message": "'password' must be at least 8 characters" }
  ]
}
```

### Response — 409 Conflict

```json
{
  "errors": [
    { "message": "Email already exists" }
  ]
}
```

---

## ✅ Critérios de Aceite

1. [ ] Usuário criado com sucesso com username, email e senha hasheada
2. [ ] Profile criado automaticamente com defaults (pt-BR, BRL)
3. [ ] NotificationPreference criada com defaults do schema
4. [ ] Validação acumulativa (Notification pattern) com todos os erros coletados
5. [ ] Username e email únicos (case-insensitive)
6. [ ] Senha nunca armazenada em texto plano (BCrypt)
7. [ ] Senha nunca retornada na resposta
8. [ ] Transação atômica (3 inserts ou nenhum)
9. [ ] Endpoint retorna 201 com dados do usuário
10. [ ] Testes unitários cobrindo todos os cenários
11. [ ] Testes de integração cobrindo persistência

---

## 📋 Ordem de Desenvolvimento

1. **Gateways** — `IProfileGateway`, `INotificationPreferenceGateway`, alterar `IUserGateway`
2. **Input/Output** — `RegisterUserInput`, `RegisterUserOutput`
3. **Interface** — `IRegisterUserUseCase`
4. **Use Case** — `RegisterUserUseCase` (com toda lógica e validação)
5. **Testes unitários** — Todos os cenários do use case (com mocks dos gateways)
6. **Infraestrutura** — Implementações dos gateways (JPA/JDBC)
7. **Controller** — `AuthController` com endpoint POST
8. **Testes de integração** — Endpoint + banco real
9. **Revisão** — Code review e ajustes finais
