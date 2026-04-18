# Task: UC-10 — EnableTwoFactor

## 📋 Resumo
A funcionalidade `EnableTwoFactor` permite que um usuário autenticado ative a Autenticação de Dois Fatores (2FA) em sua conta utilizando o padrão TOTP (Time-based One-Time Password). Para ativar, o sistema gera uma chave secreta (secret), a associa ao usuário, exibe a URI para o QR Code (para aplicativos como Google Authenticator) e exige que o usuário envie o primeiro código válido para provar que a configuração foi feita corretamente antes de salvar definitivamente.

## 🎯 Objetivo
O endpoint será dividido em duas partes (ou gerenciará um fluxo em duas etapas no mesmo Use Case dependendo do input):
1. **Solicitar Ativação:** Gerar a chave secreta TOTP, salvá-la temporariamente e devolver a URI do QR Code.
2. **Confirmar Ativação:** Receber o código TOTP gerado pelo app do usuário, validar contra a chave secreta temporária e, se sucesso, marcar o `User` com `twoFactorEnabled = true` e salvar a chave secreta de forma permanente.

*Para simplificar no padrão REST e manter stateless:* O Use Case receberá a ação desejada. Se for "GENERATE", ele gera o secret, salva no banco atrelado a um `UserToken` (do tipo `TWO_FACTOR_SETUP`) e retorna o URI. Se for "CONFIRM", ele recebe o código e o token ID, valida o TOTP e ativa na conta `User`.

## 📦 Contexto Técnico
* **Módulo Principal:** `users`
* **Prioridade:** `MÉDIA` (P2)
* **Endpoint:** `POST /v1/users/me/2fa/setup` e `POST /v1/users/me/2fa/enable`
* **Tabelas do Banco:** `users.users`, `users.user_tokens`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`
1. `domain/src/main/java/com/btree/domain/users/entities/User.java` (Adicionar propriedade `twoFactorSecret` e método `enableTwoFactor(String secret)`)
2. `domain/src/main/java/com/btree/domain/users/gateways/ITotpGateway.java` (Contrato para geração de secret, validação de código e geração de URI)

### `application`
1. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/setup/ISetupTwoFactorUseCase.java`
2. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/setup/SetupTwoFactorUseCase.java`
3. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/setup/SetupTwoFactorInput.java`
4. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/setup/SetupTwoFactorOutput.java`
5. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/enable/IEnableTwoFactorUseCase.java`
6. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/enable/EnableTwoFactorUseCase.java`
7. `application/src/main/java/com/btree/application/users/usecases/auth/twofactor/enable/EnableTwoFactorInput.java`

### `infrastructure`
1. `infrastructure/src/main/resources/db/migration/V4__add_two_factor_secret_to_users.sql` (Adicionar a coluna `two_factor_secret` na tabela `users`)
2. `infrastructure/src/main/java/com/btree/infrastructure/users/persistence/entities/UserJpaEntity.java` (Adicionar mapeamento do campo)
3. `infrastructure/src/main/java/com/btree/infrastructure/users/gateways/TotpGatewayImpl.java` (Implementação do gerador/validador TOTP usando alguma lib como `dev.samstevens.totp:totp`)

### `api`
1. `api/src/main/java/com/btree/api/users/TwoFactorController.java` (Criar um controller dedicado para endpoints de segurança de conta)
2. `api/src/main/java/com/btree/api/configurations/modules/UsersModuleConfig.java` (Adicionar os `@Bean` dos novos casos de uso)

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)
* **`User`:**
  * Adicionar o campo `twoFactorSecret` (String).
  * Criar método `enableTwoFactor(String secret)` que seta `this.twoFactorEnabled = true`, `this.twoFactorSecret = secret` e atualiza o `updatedAt`.
  * Criar método `disableTwoFactor()` que seta `this.twoFactorEnabled = false` e `this.twoFactorSecret = null`.
* Adicionar o tipo `'TWO_FACTOR_SETUP'` no Enum do Postgres e na entidade `UserTokenJpaEntity`.

### 2. Lógica do Use Case (Application)

#### A. `SetupTwoFactorUseCase`
1. Recebe o `userId`.
2. Busca o `User` pelo ID. Se não existir, erro.
3. Se `user.isTwoFactorEnabled()`, retorna erro (`UserError.TWO_FACTOR_ALREADY_ENABLED`).
4. Chama `totpGateway.generateSecret()` para criar uma nova chave forte.
5. Gera o URI do QR Code chamando `totpGateway.getUriForImage(secret, user.getEmail(), "BTree")`.
6. Salva o `secret` no banco temporariamente usando o `UserToken`:
   * Cria um `UserToken` tipo `TWO_FACTOR_SETUP`, onde o `tokenHash` é o `secret` criptografado (ou apenas o secret, já que é temporário) e expira em 15 minutos.
7. Retorna `Right(new SetupTwoFactorOutput(userToken.getId(), secret, qrCodeUri))`.

#### B. `EnableTwoFactorUseCase`
1. Recebe o `userId`, o `tokenId` e o `code` (os 6 dígitos).
2. Busca o `UserToken` pelo ID. Se não existir, for de tipo diferente, estiver expirado ou pertencer a outro `userId`, retorna erro genérico.
3. Extrai o `secret` do `userToken.getTokenHash()`.
4. Valida o código: `totpGateway.isValidCode(secret, code)`.
5. Se for inválido, retorna `Left(AuthError.INVALID_TOTP_CODE)`.
6. Se for válido:
   * Busca o `User`.
   * Chama `user.enableTwoFactor(secret)`.
   * Chama `userToken.markAsUsed()`.
   * Executa em transação: `userGateway.update(user)` e `userTokenGateway.update(userToken)`.
7. Retorna `Right(null)`.

### 3. Persistência (Infrastructure)
* Criar migration `V4__add_two_factor_secret_to_users.sql` com: `ALTER TABLE users.users ADD COLUMN two_factor_secret VARCHAR(255);`.
* Adicionar `'TWO_FACTOR_SETUP'` no enum `shared.token_type`. Para alterar enum no Postgres: `ALTER TYPE shared.token_type ADD VALUE 'TWO_FACTOR_SETUP';`.
* Adicionar o campo na `UserJpaEntity` e nos métodos `from()` e `toAggregate()`.
* Adicionar biblioteca TOTP no `pom.xml` da `infrastructure` (ex: `dev.samstevens.totp:totp` ou `aerogear/aerogear-otp-java`).

### 4. Roteamento e Injeção (API)
* Criar `TwoFactorController` com as rotas:
  * `POST /v1/users/me/2fa/setup` (Exige Bearer Token).
  * `POST /v1/users/me/2fa/enable` (Exige Bearer Token).
* O `userId` deve ser extraído do token JWT autenticado (usaríamos o `SecurityContextHolder` ou passaríamos o ID extraído na camada web para o UseCase).

---

## ⚠️ Casos de Erro Mapeados no Notification

| Erro de Domínio | Condição | Status HTTP Resultante |
| --------------- | -------- | ---------------------- |
| `UserError.TWO_FACTOR_ALREADY_ENABLED` | Usuário tenta fazer setup de 2FA mas já possui ativo | `409 Conflict` |
| `AuthError.INVALID_TOTP_CODE` | O código de 6 dígitos fornecido é inválido | `400 Bad Request` |

---

## 🌐 Contrato da API REST

### 1. Setup Request
`POST /v1/users/me/2fa/setup`
```json
{}
```

### 1. Setup Response (Sucesso)
```json
{
  "data": {
    "setup_token_id": "f8a7c2e3...",
    "secret": "JBSWY3DPEHPK3PXP",
    "qr_code_uri": "otpauth://totp/BTree:user@email.com?secret=JBSWY3DPEHPK3PXP&issuer=BTree"
  },
  "success": true,
  "timestamp": "2026-04-06T12:00:00Z"
}
```

### 2. Enable Request
`POST /v1/users/me/2fa/enable`
```json
{
  "setup_token_id": "f8a7c2e3...",
  "code": "123456"
}
```

### 2. Enable Response (Sucesso)
```json
{
  "data": null,
  "success": true,
  "timestamp": "2026-04-06T12:05:00Z"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida
1. Adicionar dependência TOTP e criar `ITotpGateway`.
2. Modificar entidade `User` e adicionar nova migration.
3. Criar os Use Cases de Setup e Enable.
4. Implementar `TotpGatewayImpl` e atualizar repositórios/JpaEntities.
5. Criar o Controller Autenticado (`TwoFactorController`).
6. Configurar `@Bean`s e testar o fluxo.