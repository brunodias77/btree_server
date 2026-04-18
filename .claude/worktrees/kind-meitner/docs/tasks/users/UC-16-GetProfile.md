# Task: UC-16 — GetProfile

## 📋 Resumo

Permite que o usuário autenticado consulte seu próprio perfil completo — nome, CPF, data de nascimento, idioma, moeda, preferências de newsletter e demais dados pessoais cadastrados. É uma query de leitura pura, fundamental para que o frontend renderize a tela de perfil e pré-popule o formulário de edição (UC-15).

## 🎯 Objetivo

Implementar o endpoint `GET /api/v1/users/me/profile` que, dado o `userId` extraído do JWT, busca o perfil correspondente na tabela `users.profiles` e o retorna como `GetProfileOutput`. Nenhum estado é mutado — use case de leitura pura que implementa `QueryUseCase`.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `CRÍTICO`
- **Endpoint:** `GET /api/v1/users/me/profile`
- **Tabelas do Banco:** `users.profiles`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/gateway/ProfileGateway.java` — **verificar**: confirmar que `findByUserId(UserId)` foi adicionado no UC-15; se não, adicionar agora
2. `domain/src/main/java/com/btree/domain/user/error/ProfileError.java` — **verificar**: confirmar que `PROFILE_NOT_FOUND` foi adicionado no UC-15; se não, adicionar agora

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/profile/GetProfileCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/profile/GetProfileOutput.java` — **criar**
3. `application/src/main/java/com/btree/application/usecase/user/profile/GetProfileUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/ProfilePostgresGateway.java` — **verificar**: confirmar que `findByUserId` foi implementado no UC-15; se não, implementar agora

### `api`

1. `api/src/main/java/com/btree/api/user/profile/GetProfileResponse.java` — **criar**
2. `api/src/main/java/com/btree/api/user/profile/ProfileController.java` — **alterar**: adicionar endpoint `GET /v1/users/me/profile` ao controller existente
3. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean` do novo use case

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Gateway e Erros de Domínio (Domain)

Este use case não requer criação de entidades ou eventos — apenas leitura via gateway já existente. Verificar que `ProfileGateway` possui:

```java
// ProfileGateway.java — verificar presença (adicionado no UC-15)

/**
 * Busca o perfil ativo do usuário pelo seu {@code userId}.
 * Retorna {@link Optional#empty()} se o perfil não existir
 * ou estiver soft-deletado ({@code deleted_at IS NOT NULL}).
 */
Optional<Profile> findByUserId(UserId userId);
```

E que `ProfileError` possui:

```java
// ProfileError.java — verificar presença (adicionado no UC-15)
public static final Error PROFILE_NOT_FOUND = new Error("Perfil não encontrado para o usuário informado");
```

Se UC-15 ainda não foi implementado, adicionar ambos antes de prosseguir.

### 2. Contrato de Entrada/Saída (Application)

**`GetProfileCommand.java`**:

```java
package com.btree.application.usecase.user.profile;

/**
 * Comando de entrada para UC-16 — GetProfile.
 *
 * <p>Carrega apenas o {@code userId} extraído do JWT pelo controller.
 * O usuário só pode consultar seu próprio perfil.
 *
 * @param userId ID do usuário autenticado (extraído do JWT)
 */
public record GetProfileCommand(String userId) {}
```

**`GetProfileOutput.java`**:

```java
package com.btree.application.usecase.user.profile;

import com.btree.domain.user.entity.Profile;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Saída do caso de uso UC-16 — GetProfile.
 *
 * <p>Retorna todos os campos públicos do perfil.
 * Omite dados sensíveis de outros aggregates (passwordHash, tokens, etc.).
 *
 * @param id                   UUID do perfil
 * @param userId               UUID do usuário proprietário
 * @param firstName            primeiro nome
 * @param lastName             sobrenome
 * @param displayName          nome de exibição (gerado automaticamente)
 * @param avatarUrl            URL do avatar
 * @param birthDate            data de nascimento
 * @param gender               gênero (texto livre)
 * @param cpf                  CPF no formato XXX.XXX.XXX-XX
 * @param preferredLanguage    idioma preferido (ex: "pt-BR")
 * @param preferredCurrency    moeda preferida (ex: "BRL")
 * @param newsletterSubscribed preferência de newsletter
 * @param acceptedTermsAt      data de aceite dos termos de uso
 * @param acceptedPrivacyAt    data de aceite da política de privacidade
 * @param createdAt            data de criação do perfil
 * @param updatedAt            data da última atualização
 */
public record GetProfileOutput(
        String id,
        String userId,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        LocalDate birthDate,
        String gender,
        String cpf,
        String preferredLanguage,
        String preferredCurrency,
        boolean newsletterSubscribed,
        Instant acceptedTermsAt,
        Instant acceptedPrivacyAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static GetProfileOutput from(final Profile profile) {
        return new GetProfileOutput(
                profile.getId().getValue().toString(),
                profile.getUserId().getValue().toString(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getBirthDate(),
                profile.getGender(),
                profile.getCpf(),
                profile.getPreferredLanguage(),
                profile.getPreferredCurrency(),
                profile.isNewsletterSubscribed(),
                profile.getAcceptedTermsAt(),
                profile.getAcceptedPrivacyAt(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
```

> **`GetProfileOutput` vs `UpdateProfileOutput`:** o output de GET inclui `acceptedTermsAt`, `acceptedPrivacyAt` e `createdAt` — campos relevantes para leitura mas desnecessários no retorno de uma edição. Outputs distintos por use case é a prática correta — evita acoplamento entre operações.

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.user.profile;

import com.btree.domain.user.error.ProfileError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.usecase.QueryUseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Right;

/**
 * Caso de uso UC-16 — GetProfile [QRY P0].
 *
 * <p>Consulta o perfil completo do usuário autenticado.
 * Operação de leitura pura — nenhum estado é mutado.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato do {@code userId}.</li>
 *   <li>Busca o perfil via {@code profileGateway.findByUserId()}.</li>
 *   <li>Retorna {@code Left(Notification)} se não encontrado.</li>
 *   <li>Projeta e retorna {@code Right(GetProfileOutput)}.</li>
 * </ol>
 *
 * <p><b>Sem transação:</b> queries de leitura pura não necessitam de
 * {@code TransactionManager} explícito — o {@code @Transactional(readOnly = true)}
 * do gateway é suficiente para garantir consistência de leitura.
 */
public class GetProfileUseCase implements QueryUseCase<GetProfileCommand, GetProfileOutput> {

    private final ProfileGateway profileGateway;

    public GetProfileUseCase(final ProfileGateway profileGateway) {
        this.profileGateway = profileGateway;
    }

    @Override
    public Either<Notification, GetProfileOutput> execute(final GetProfileCommand command) {
        final var notification = Notification.create();

        // 1. Validar presença e formato do userId
        if (command.userId() == null || command.userId().isBlank()) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        final UserId userId;
        try {
            userId = UserId.from(UUID.fromString(command.userId()));
        } catch (IllegalArgumentException e) {
            notification.append(UserError.USER_NOT_FOUND);
            return Left(notification);
        }

        // 2. Buscar perfil pelo userId
        final var profileOpt = profileGateway.findByUserId(userId);
        if (profileOpt.isEmpty()) {
            notification.append(ProfileError.PROFILE_NOT_FOUND);
            return Left(notification);
        }

        // 3. Projetar e retornar
        return Right(GetProfileOutput.from(profileOpt.get()));
    }
}
```

> **Por que não usar `TransactionManager`?** Queries puras delegam o controle transacional ao gateway concreto via `@Transactional(readOnly = true)`. Envolver em `transactionManager.execute()` seria redundante e adicionaria overhead desnecessário. O padrão do projeto prevê `TransactionManager` apenas para operações de escrita.

### 4. Persistência (Infrastructure)

Nenhum arquivo novo de persistência é necessário — `ProfileJpaRepository.findActiveByUserId` e `ProfilePostgresGateway.findByUserId` foram criados no UC-15.

Caso UC-15 não tenha sido implementado ainda, garantir que `ProfileJpaRepository` contenha:

```java
// ProfileJpaRepository.java — verificar/adicionar se UC-15 não foi feito

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Busca o perfil ativo pelo userId.
 * Exclui perfis com soft-delete ({@code deleted_at IS NOT NULL}).
 */
@Query("""
    SELECT p FROM ProfileJpaEntity p
    WHERE p.user.id = :userId
      AND p.deletedAt IS NULL
    """)
Optional<ProfileJpaEntity> findActiveByUserId(@Param("userId") UUID userId);
```

E que `ProfilePostgresGateway` contenha:

```java
// ProfilePostgresGateway.java — verificar/adicionar se UC-15 não foi feito

@Override
@Transactional(readOnly = true)
public Optional<Profile> findByUserId(final UserId userId) {
    return profileJpaRepository
            .findActiveByUserId(userId.getValue())
            .map(ProfileJpaEntity::toAggregate);
}
```

### 5. Roteamento e Injeção (API)

**`GetProfileResponse.java`**:

```java
package com.btree.api.user.profile;

import com.btree.application.usecase.user.profile.GetProfileOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO HTTP de saída para {@code GET /api/v1/users/me/profile}.
 *
 * <p>Inclui todos os campos públicos do perfil, incluindo timestamps
 * de auditoria e aceite de termos — úteis para o frontend exibir
 * informações de conformidade (LGPD).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetProfileResponse(
        String id,
        @JsonProperty("user_id")               String userId,
        @JsonProperty("first_name")            String firstName,
        @JsonProperty("last_name")             String lastName,
        @JsonProperty("display_name")          String displayName,
        @JsonProperty("avatar_url")            String avatarUrl,
        @JsonProperty("birth_date")            LocalDate birthDate,
        String gender,
        String cpf,
        @JsonProperty("preferred_language")    String preferredLanguage,
        @JsonProperty("preferred_currency")    String preferredCurrency,
        @JsonProperty("newsletter_subscribed") boolean newsletterSubscribed,
        @JsonProperty("accepted_terms_at")     Instant acceptedTermsAt,
        @JsonProperty("accepted_privacy_at")   Instant acceptedPrivacyAt,
        @JsonProperty("created_at")            Instant createdAt,
        @JsonProperty("updated_at")            Instant updatedAt
) {
    public static GetProfileResponse from(final GetProfileOutput output) {
        return new GetProfileResponse(
                output.id(),
                output.userId(),
                output.firstName(),
                output.lastName(),
                output.displayName(),
                output.avatarUrl(),
                output.birthDate(),
                output.gender(),
                output.cpf(),
                output.preferredLanguage(),
                output.preferredCurrency(),
                output.newsletterSubscribed(),
                output.acceptedTermsAt(),
                output.acceptedPrivacyAt(),
                output.createdAt(),
                output.updatedAt()
        );
    }
}
```

**`ProfileController.java`** — adicionar endpoint `GET` ao controller existente (criado no UC-15):

```java
package com.btree.api.user.profile;

import com.btree.application.usecase.user.profile.GetProfileCommand;
import com.btree.application.usecase.user.profile.GetProfileUseCase;
import com.btree.application.usecase.user.profile.UpdateProfileCommand;
import com.btree.application.usecase.user.profile.UpdateProfileUseCase;
import com.btree.shared.domain.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de perfil do usuário autenticado.
 *
 * <p>Rotas protegidas — requer JWT válido no header
 * {@code Authorization: Bearer <token>}.
 */
@RestController
@RequestMapping("/v1/users/me/profile")
@Tag(name = "Profile", description = "Gerenciamento do perfil do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final GetProfileUseCase getProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;

    public ProfileController(
            final GetProfileUseCase getProfileUseCase,
            final UpdateProfileUseCase updateProfileUseCase
    ) {
        this.getProfileUseCase = getProfileUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
    }

    // ── UC-16: GetProfile ─────────────────────────────────────────────────

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Consultar perfil",
            description = "Retorna o perfil completo do usuário autenticado, incluindo " +
                          "dados pessoais, preferências e timestamps de aceite de termos (LGPD)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Perfil não encontrado para o usuário")
    })
    public GetProfileResponse get() {
        final String userId = currentUserId();

        return GetProfileResponse.from(
                getProfileUseCase.execute(new GetProfileCommand(userId))
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── UC-15: UpdateProfile ──────────────────────────────────────────────

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Atualizar perfil",
            description = "Edita os dados pessoais do usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Formato de campo inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "409", description = "Conflito de versão ou CPF já em uso"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public UpdateProfileResponse update(@Valid @RequestBody final UpdateProfileRequest request) {
        final String userId = currentUserId();

        return UpdateProfileResponse.from(
                updateProfileUseCase.execute(new UpdateProfileCommand(
                        userId,
                        request.firstName(),
                        request.lastName(),
                        request.cpf(),
                        request.birthDate(),
                        request.gender(),
                        request.preferredLanguage(),
                        request.preferredCurrency(),
                        request.newsletterSubscribed()
                )).getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
```

**`UseCaseConfig.java`** — adicionar `@Bean`:

```java
// UseCaseConfig.java — adicionar

@Bean
public GetProfileUseCase getProfileUseCase(final ProfileGateway profileGateway) {
    return new GetProfileUseCase(profileGateway);
}
```

> **Sem `TransactionManager` no bean:** `GetProfileUseCase` não usa `TransactionManager` — recebe apenas `ProfileGateway`. O Spring injeta o gateway concreto que já gerencia sua própria transação de leitura.

---

## ⚠️ Casos de Erro Mapeados

| Erro de Domínio                             | Condição                                       | Status HTTP Resultante     |
| ------------------------------------------- | ---------------------------------------------- | -------------------------- |
| `UserError.USER_NOT_FOUND`                  | `userId` nulo, vazio ou UUID inválido no JWT   | `422 Unprocessable Entity` |
| `ProfileError.PROFILE_NOT_FOUND`            | Nenhum perfil ativo encontrado para o `userId` | `422 Unprocessable Entity` |
| `AuthenticationException` (Spring Security) | JWT ausente ou inválido no header              | `401 Unauthorized`         |

> **Nota:** `PROFILE_NOT_FOUND` retorna `422` em vez de `404` porque o perfil é criado automaticamente no registro (UC-01). Se o perfil não existir, é um estado inesperado do sistema — não um recurso inexistente por escolha do usuário.

---

## 🌐 Contrato da API REST

### Request

```http
GET /api/v1/users/me/profile
Authorization: Bearer <access_token>
```

Sem body.

### Response (Sucesso — 200 OK)

```json
{
  "id": "019486ab-c123-7def-a456-789012345678",
  "user_id": "019486ab-c123-7def-a456-789012345679",
  "first_name": "João",
  "last_name": "Silva",
  "display_name": "João Silva",
  "avatar_url": null,
  "birth_date": "1992-05-15",
  "gender": "male",
  "cpf": "529.982.247-25",
  "preferred_language": "pt-BR",
  "preferred_currency": "BRL",
  "newsletter_subscribed": true,
  "accepted_terms_at": "2026-01-10T08:30:00Z",
  "accepted_privacy_at": "2026-01-10T08:30:00Z",
  "created_at": "2026-01-10T08:30:00Z",
  "updated_at": "2026-04-09T14:32:00Z"
}
```

### Response (Perfil recém-criado — campos não preenchidos)

```json
{
  "id": "019486ab-c123-7def-a456-789012345678",
  "user_id": "019486ab-c123-7def-a456-789012345679",
  "first_name": null,
  "last_name": null,
  "display_name": null,
  "avatar_url": null,
  "birth_date": null,
  "gender": null,
  "cpf": null,
  "preferred_language": "pt-BR",
  "preferred_currency": "BRL",
  "newsletter_subscribed": false,
  "accepted_terms_at": null,
  "accepted_privacy_at": null,
  "created_at": "2026-04-09T14:00:00Z",
  "updated_at": "2026-04-09T14:00:00Z"
}
```

> **`@JsonInclude(NON_NULL)`** no response garante que campos `null` são omitidos da resposta — comportamento mais limpo para o frontend, que não precisa checar `null` para cada campo opcional.

### Response (Erro — 401 Unauthorized)

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Autenticação necessária.",
  "timestamp": "2026-04-09T14:32:00Z",
  "path": "/api/v1/users/me/profile"
}
```

### Response (Erro — 422 Perfil não encontrado)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Perfil não encontrado para o usuário informado",
  "errors": ["Perfil não encontrado para o usuário informado"],
  "timestamp": "2026-04-09T14:32:00Z",
  "path": "/api/v1/users/me/profile"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

> Se UC-15 já foi implementado, os itens 1 e 2 são apenas verificações.

1. **`ProfileError.java`** — verificar presença de `PROFILE_NOT_FOUND`; adicionar se ausente.
2. **`ProfileGateway.java`** — verificar presença de `findByUserId(UserId)`; adicionar se ausente.
3. **`ProfileJpaRepository.java`** — verificar presença de `findActiveByUserId`; adicionar se ausente.
4. **`ProfilePostgresGateway.java`** — verificar presença de `findByUserId`; adicionar se ausente.
5. **`GetProfileCommand.java`** — record com `userId`.
6. **`GetProfileOutput.java`** — record completo com factory `from(Profile)`.
7. **`GetProfileUseCase.java`** — lógica com `Either`, sem `TransactionManager`.
8. **`UseCaseConfig.java`** — registrar `@Bean` do `GetProfileUseCase`.
9. **`GetProfileResponse.java`** — record com `@JsonInclude(NON_NULL)` e factory `from(GetProfileOutput)`.
10. **`ProfileController.java`** — adicionar endpoint `GET` e refatorar construtor para receber ambos os use cases.
11. **Testes unitários** — `GetProfileUseCaseTest` em `application/` com Mockito.
12. **Testes de integração** — `ProfilePostgresGatewayIT` em `infrastructure/` (cobrir `findByUserId`).

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `GetProfileUseCaseTest`

| Cenário                                                | Comportamento esperado                                    |
| ------------------------------------------------------ | --------------------------------------------------------- |
| `userId` válido com perfil existente e completo        | `Right(GetProfileOutput)` com todos os campos populados   |
| `userId` válido com perfil recém-criado (campos nulos) | `Right(GetProfileOutput)` com campos opcionais nulos      |
| `userId` válido sem perfil cadastrado                  | `Left(Notification)` com `ProfileError.PROFILE_NOT_FOUND` |
| `userId` nulo                                          | `Left(Notification)` com `UserError.USER_NOT_FOUND`       |
| `userId` em branco                                     | `Left(Notification)` com `UserError.USER_NOT_FOUND`       |
| `userId` com formato UUID inválido                     | `Left(Notification)` com `UserError.USER_NOT_FOUND`       |
| Gateway lança `DataAccessException`                    | Exceção propagada (não capturada — sem `Try` no use case) |

> **Nota sobre o último cenário:** diferente dos use cases de escrita, `GetProfileUseCase` não usa `Try` — exceções de infraestrutura não são convertidas em `Left`. Elas propagam para o `GlobalExceptionHandler` como `500 Internal Server Error`. Isso é intencional para queries: falhas de leitura são erros inesperados do sistema, não erros de negócio tratáveis.

### Integração (`infrastructure/`) — `ProfilePostgresGatewayIT`

| Cenário                                                                               | Verificação                                                |
| ------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `findByUserId()` para usuário com perfil ativo                                        | Retorna `Optional` com perfil completo                     |
| `findByUserId()` para usuário sem perfil                                              | Retorna `Optional.empty()`                                 |
| `findByUserId()` para perfil com `deleted_at` preenchido                              | Retorna `Optional.empty()` (soft-delete respeitado)        |
| `findByUserId()` retorna campos `preferredLanguage` e `preferredCurrency` corretos    | Valores padrão `"pt-BR"` e `"BRL"` para perfil novo        |
| `findByUserId()` retorna `displayName` derivado de `firstName` + `lastName`           | `"João Silva"` quando ambos preenchidos                    |
| `findByUserId()` retorna `displayName` nulo quando `firstName` e `lastName` são nulos | `null` no campo `displayName`                              |
| Dois usuários distintos com perfis                                                    | Cada `findByUserId` retorna apenas o perfil do seu usuário |
