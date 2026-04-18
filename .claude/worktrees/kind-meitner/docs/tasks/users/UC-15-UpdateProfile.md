# Task: UC-15 — UpdateProfile

## 📋 Resumo

Permite que o usuário autenticado edite seu perfil pessoal — nome, sobrenome, CPF, data de nascimento, gênero, idioma preferido, moeda preferida e preferência de newsletter. É uma das funcionalidades centrais da experiência pós-cadastro, sem a qual o usuário não consegue completar dados obrigatórios para checkout (CPF para emissão de NF-e, por exemplo).

## 🎯 Objetivo

Implementar o endpoint `PUT /api/v1/users/me/profile` que recebe os campos editáveis do perfil, valida as regras de domínio (formato de CPF, tamanho de campos, unicidade de CPF), persiste via optimistic locking e retorna o perfil atualizado. O `userId` é extraído do JWT — o usuário só pode editar seu próprio perfil.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `CRÍTICO`
- **Endpoint:** `PUT /api/v1/users/me/profile`
- **Tabelas do Banco:** `users.profiles`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/gateway/ProfileGateway.java` — **alterar**: adicionar métodos `findByUserId(UserId)`, `update(Profile)` e `existsByCpfAndNotUserId(String cpf, UserId userId)`
2. `domain/src/main/java/com/btree/domain/user/entity/Profile.java` — **alterar**: adicionar método de mutação `updatePersonalData(...)` e ajustar validação de CPF via `ProfileValidator`
3. `domain/src/main/java/com/btree/domain/user/validator/ProfileValidator.java` — **criar**: validação completa do aggregate `Profile`
4. `domain/src/main/java/com/btree/domain/user/error/ProfileError.java` — **alterar**: adicionar constantes de erro faltantes

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/profile/UpdateProfileCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/profile/UpdateProfileOutput.java` — **criar**
3. `application/src/main/java/com/btree/application/usecase/user/profile/UpdateProfileUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/ProfileJpaRepository.java` — **alterar**: adicionar `existsByCpfAndUserIdNot`
2. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/ProfilePostgresGateway.java` — **alterar**: implementar `findByUserId`, `update` e `existsByCpfAndNotUserId`

### `api`

1. `api/src/main/java/com/btree/api/user/profile/UpdateProfileRequest.java` — **criar**
2. `api/src/main/java/com/btree/api/user/profile/UpdateProfileResponse.java` — **criar**
3. `api/src/main/java/com/btree/api/user/profile/ProfileController.java` — **criar**
4. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)

**`ProfileError.java`** — adicionar constantes faltantes ao arquivo existente:

```java
package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public final class ProfileError {
    private ProfileError() {}

    // Já existentes
    public static final Error CPF_INVALID            = new Error("CPF inválido");
    public static final Error NAME_TOO_LONG          = new Error("Primeiro nome ou sobrenome excede o tamanho máximo de 100 caracteres");
    public static final Error INVALID_PHONE_NUMBER   = new Error("Número de telefone deve seguir o formato E.164");

    // Adicionar
    public static final Error CPF_ALREADY_IN_USE     = new Error("CPF já está em uso por outro usuário");
    public static final Error FIRST_NAME_EMPTY       = new Error("'firstName' não pode estar vazio quando fornecido");
    public static final Error PREFERRED_LANGUAGE_INVALID = new Error("'preferredLanguage' deve ter entre 2 e 10 caracteres");
    public static final Error PREFERRED_CURRENCY_INVALID = new Error("'preferredCurrency' deve ter exatamente 3 caracteres");
    public static final Error PROFILE_NOT_FOUND      = new Error("Perfil não encontrado para o usuário informado");
}
```

**`ProfileValidator.java`** — criar validador completo:

```java
package com.btree.domain.user.validator;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.error.ProfileError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;
import com.btree.shared.valueobject.Cpf;

public class ProfileValidator extends Validator {

    private static final int NAME_MAX_LENGTH     = 100;
    private static final int LANGUAGE_MAX_LENGTH = 10;
    private static final int LANGUAGE_MIN_LENGTH = 2;
    private static final int CURRENCY_LENGTH     = 3;

    private final Profile profile;

    public ProfileValidator(final Profile profile, final ValidationHandler handler) {
        super(handler);
        this.profile = profile;
    }

    @Override
    public void validate() {
        checkFirstName();
        checkLastName();
        checkCpf();
        checkPreferredLanguage();
        checkPreferredCurrency();
    }

    private void checkFirstName() {
        final var firstName = profile.getFirstName();
        if (firstName != null && firstName.length() > NAME_MAX_LENGTH) {
            validationHandler().append(ProfileError.NAME_TOO_LONG);
        }
    }

    private void checkLastName() {
        final var lastName = profile.getLastName();
        if (lastName != null && lastName.length() > NAME_MAX_LENGTH) {
            validationHandler().append(ProfileError.NAME_TOO_LONG);
        }
    }

    private void checkCpf() {
        final var cpf = profile.getCpf();
        if (cpf != null && !cpf.isBlank()) {
            try {
                Cpf.of(cpf);
            } catch (IllegalArgumentException e) {
                validationHandler().append(ProfileError.CPF_INVALID);
            }
        }
    }

    private void checkPreferredLanguage() {
        final var lang = profile.getPreferredLanguage();
        if (lang != null && (lang.length() < LANGUAGE_MIN_LENGTH || lang.length() > LANGUAGE_MAX_LENGTH)) {
            validationHandler().append(ProfileError.PREFERRED_LANGUAGE_INVALID);
        }
    }

    private void checkPreferredCurrency() {
        final var currency = profile.getPreferredCurrency();
        if (currency != null && currency.length() != CURRENCY_LENGTH) {
            validationHandler().append(ProfileError.PREFERRED_CURRENCY_INVALID);
        }
    }
}
```

**`Profile.java`** — adicionar método de mutação `updatePersonalData` e sobrescrever `validate`:

```java
// Profile.java — adicionar método de mutação

/**
 * Atualiza os dados pessoais editáveis do perfil.
 *
 * <p>Campos nulos são aceitos — representam ausência de valor (usuário
 * não preencheu), não remoção de dado existente. O campo {@code cpf}
 * é uma exceção: se fornecido, deve ser válido (validado externamente
 * pelo use case via {@code ProfileValidator}).
 */
public void updatePersonalData(
        final String firstName,
        final String lastName,
        final String cpf,
        final java.time.LocalDate birthDate,
        final String gender,
        final String preferredLanguage,
        final String preferredCurrency,
        final boolean newsletterSubscribed
) {
    this.firstName             = firstName;
    this.lastName              = lastName;
    this.displayName           = buildDisplayName(firstName, lastName);
    this.cpf                   = cpf;
    this.birthDate             = birthDate;
    this.gender                = gender;
    this.preferredLanguage     = preferredLanguage != null ? preferredLanguage : "pt-BR";
    this.preferredCurrency     = preferredCurrency != null ? preferredCurrency : "BRL";
    this.newsletterSubscribed  = newsletterSubscribed;
    this.updatedAt             = java.time.Instant.now();
}

private static String buildDisplayName(final String firstName, final String lastName) {
    if (firstName == null && lastName == null) return null;
    final String first = firstName != null ? firstName.trim() : "";
    final String last  = lastName  != null ? " " + lastName.trim() : "";
    return (first + last).trim();
}

// Sobrescrever validate() para usar o ProfileValidator
@Override
public void validate(final com.btree.shared.validation.ValidationHandler handler) {
    new com.btree.domain.user.validator.ProfileValidator(this, handler).validate();
}
```

**`ProfileGateway.java`** — adicionar métodos necessários:

```java
package com.btree.domain.user.gateway;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.identifier.UserId;

import java.util.Optional;

public interface ProfileGateway {

    Profile create(Profile profile);

    /**
     * Atualiza um perfil existente respeitando optimistic locking.
     * Lança {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * em caso de conflito de versão.
     */
    Profile update(Profile profile);

    /**
     * Busca o perfil pelo {@code userId} do usuário proprietário.
     */
    Optional<Profile> findByUserId(UserId userId);

    /**
     * Verifica se outro usuário já possui o CPF informado.
     * Usado para garantir unicidade de CPF antes de persistir.
     *
     * @param cpf    CPF formatado (XXX.XXX.XXX-XX) a verificar
     * @param userId ID do usuário atual — excluído da verificação
     * @return {@code true} se outro usuário já usa este CPF
     */
    boolean existsByCpfAndNotUserId(String cpf, UserId userId);
}
```

### 2. Contrato de Entrada/Saída (Application)

**`UpdateProfileCommand.java`**:

```java
package com.btree.application.usecase.user.profile;

import java.time.LocalDate;

/**
 * Comando de entrada para UC-15 — UpdateProfile.
 *
 * <p>Todos os campos são opcionais exceto {@code userId}.
 * Campos nulos representam ausência de valor — o perfil
 * armazenará null para esses campos.
 *
 * @param userId               ID do usuário autenticado (extraído do JWT)
 * @param firstName            primeiro nome (máx. 100 chars)
 * @param lastName             sobrenome (máx. 100 chars)
 * @param cpf                  CPF no formato XXX.XXX.XXX-XX
 * @param birthDate            data de nascimento
 * @param gender               gênero (texto livre — ex: "male", "female", "non-binary")
 * @param preferredLanguage    idioma preferido (ex: "pt-BR", "en-US")
 * @param preferredCurrency    moeda preferida (ex: "BRL", "USD") — 3 chars ISO 4217
 * @param newsletterSubscribed preferência de newsletter
 */
public record UpdateProfileCommand(
        String userId,
        String firstName,
        String lastName,
        String cpf,
        LocalDate birthDate,
        String gender,
        String preferredLanguage,
        String preferredCurrency,
        boolean newsletterSubscribed
) {}
```

**`UpdateProfileOutput.java`**:

```java
package com.btree.application.usecase.user.profile;

import com.btree.domain.user.entity.Profile;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Saída do caso de uso UC-15 — UpdateProfile.
 *
 * <p>Omite dados sensíveis. Retorna o perfil completo após a atualização.
 */
public record UpdateProfileOutput(
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
        Instant updatedAt
) {
    public static UpdateProfileOutput from(final Profile profile) {
        return new UpdateProfileOutput(
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
                profile.getUpdatedAt()
        );
    }
}
```

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.user.profile;

import com.btree.domain.user.error.ProfileError;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.validator.ProfileValidator;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-15 — UpdateProfile [CMD P0].
 *
 * <p>Edita os dados pessoais do perfil do usuário autenticado.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato do {@code userId}.</li>
 *   <li>Busca o perfil pelo {@code userId} — retorna erro se não encontrado.</li>
 *   <li>Verifica unicidade de CPF (se fornecido) — outro usuário não pode ter o mesmo CPF.</li>
 *   <li>Chama {@code profile.updatePersonalData(...)} para mutar o aggregate.</li>
 *   <li>Valida invariantes do aggregate via {@link ProfileValidator}.</li>
 *   <li>Persiste via {@code profileGateway.update()} dentro da transação.</li>
 *   <li>Retorna {@link UpdateProfileOutput} com o perfil atualizado.</li>
 * </ol>
 *
 * <p><b>Optimistic Locking:</b> {@code ProfileJpaEntity} possui {@code @Version}.
 * Conflitos simultâneos disparam {@code ObjectOptimisticLockingFailureException},
 * capturado pelo {@code GlobalExceptionHandler} como 409.
 */
public class UpdateProfileUseCase implements UseCase<UpdateProfileCommand, UpdateProfileOutput> {

    private final ProfileGateway profileGateway;
    private final TransactionManager transactionManager;

    public UpdateProfileUseCase(
            final ProfileGateway profileGateway,
            final TransactionManager transactionManager
    ) {
        this.profileGateway = profileGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, UpdateProfileOutput> execute(final UpdateProfileCommand command) {
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

        // 2. Buscar perfil existente
        final var profileOpt = profileGateway.findByUserId(userId);
        if (profileOpt.isEmpty()) {
            notification.append(ProfileError.PROFILE_NOT_FOUND);
            return Left(notification);
        }

        final var profile = profileOpt.get();

        // 3. Verificar unicidade de CPF (se fornecido e diferente do atual)
        final var cpf = command.cpf();
        if (cpf != null && !cpf.isBlank()) {
            if (profileGateway.existsByCpfAndNotUserId(cpf, userId)) {
                notification.append(ProfileError.CPF_ALREADY_IN_USE);
            }
        }

        if (notification.hasError()) {
            return Left(notification);
        }

        // 4. Mutar o aggregate
        profile.updatePersonalData(
                command.firstName(),
                command.lastName(),
                cpf != null && cpf.isBlank() ? null : cpf,
                command.birthDate(),
                command.gender(),
                command.preferredLanguage(),
                command.preferredCurrency(),
                command.newsletterSubscribed()
        );

        // 5. Validar invariantes após mutação
        profile.validate(notification);
        if (notification.hasError()) {
            return Left(notification);
        }

        // 6. Persistir dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            final var updated = profileGateway.update(profile);
            return UpdateProfileOutput.from(updated);
        })).toEither().mapLeft(Notification::create);
    }
}
```

### 4. Persistência (Infrastructure)

**`ProfileJpaRepository.java`** — adicionar método de unicidade de CPF:

```java
// ProfileJpaRepository.java — adicionar

import java.util.Optional;
import java.util.UUID;

/**
 * Verifica se existe perfil com o CPF informado pertencente
 * a outro usuário (excluindo o próprio {@code userId}).
 * Usado para validar unicidade de CPF no update.
 */
boolean existsByCpfAndUserIdNot(String cpf, UUID userId);

/**
 * Busca o perfil pelo userId do proprietário.
 * Exclui perfis com soft-delete ({@code deleted_at IS NOT NULL}).
 */
@Query("SELECT p FROM ProfileJpaEntity p WHERE p.user.id = :userId AND p.deletedAt IS NULL")
Optional<ProfileJpaEntity> findActiveByUserId(@Param("userId") UUID userId);
```

**`ProfilePostgresGateway.java`** — implementar os novos métodos:

```java
package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.gateway.ProfileGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.user.entity.ProfileJpaEntity;
import com.btree.infrastructure.user.entity.UserJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional
public class ProfilePostgresGateway implements ProfileGateway {

    private final ProfileJpaRepository profileJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public ProfilePostgresGateway(
            final ProfileJpaRepository profileJpaRepository,
            final UserJpaRepository userJpaRepository
    ) {
        this.profileJpaRepository = profileJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Profile create(final Profile profile) {
        final UserJpaEntity userEntity = userJpaRepository
                .findById(profile.getUserId().getValue())
                .orElseThrow(() -> NotFoundException.with(
                        "Usuário não encontrado: " + profile.getUserId().getValue()));
        return profileJpaRepository
                .save(ProfileJpaEntity.from(profile, userEntity))
                .toAggregate();
    }

    @Override
    public Profile update(final Profile profile) {
        final var entity = profileJpaRepository
                .findById(profile.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(
                        "Perfil não encontrado: " + profile.getId().getValue()));
        entity.updateFrom(profile);
        return profileJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Profile> findByUserId(final UserId userId) {
        return profileJpaRepository
                .findActiveByUserId(userId.getValue())
                .map(ProfileJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCpfAndNotUserId(final String cpf, final UserId userId) {
        return profileJpaRepository
                .existsByCpfAndUserIdNot(cpf, userId.getValue());
    }
}
```

> **`updateFrom` no `ProfileJpaEntity`** já existe e atualiza os campos mutáveis preservando `id`, `user` e `version`. Nenhuma alteração necessária na entity JPA.

### 5. Roteamento e Injeção (API)

**`UpdateProfileRequest.java`**:

```java
package com.btree.api.user.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO HTTP de entrada para {@code PUT /api/v1/users/me/profile}.
 *
 * <p>Todos os campos são opcionais. Campos ausentes no JSON são tratados
 * como {@code null} e sobrescrevem o valor atual do perfil.
 * O cliente deve sempre enviar o payload completo com os valores desejados.
 */
public record UpdateProfileRequest(

        @Size(max = 100, message = "firstName deve ter no máximo 100 caracteres")
        @JsonProperty("first_name")
        String firstName,

        @Size(max = 100, message = "lastName deve ter no máximo 100 caracteres")
        @JsonProperty("last_name")
        String lastName,

        @Pattern(
            regexp = "^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$",
            message = "cpf deve estar no formato XXX.XXX.XXX-XX"
        )
        String cpf,

        @JsonProperty("birth_date")
        LocalDate birthDate,

        @Size(max = 20, message = "gender deve ter no máximo 20 caracteres")
        String gender,

        @Size(min = 2, max = 10, message = "preferredLanguage deve ter entre 2 e 10 caracteres")
        @JsonProperty("preferred_language")
        String preferredLanguage,

        @Size(min = 3, max = 3, message = "preferredCurrency deve ter exatamente 3 caracteres")
        @JsonProperty("preferred_currency")
        String preferredCurrency,

        @JsonProperty("newsletter_subscribed")
        boolean newsletterSubscribed
) {}
```

**`UpdateProfileResponse.java`**:

```java
package com.btree.api.user.profile;

import com.btree.application.usecase.user.profile.UpdateProfileOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProfileResponse(
        String id,
        @JsonProperty("user_id")   String userId,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name")  String lastName,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("birth_date") LocalDate birthDate,
        String gender,
        String cpf,
        @JsonProperty("preferred_language") String preferredLanguage,
        @JsonProperty("preferred_currency") String preferredCurrency,
        @JsonProperty("newsletter_subscribed") boolean newsletterSubscribed,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static UpdateProfileResponse from(final UpdateProfileOutput output) {
        return new UpdateProfileResponse(
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
                output.updatedAt()
        );
    }
}
```

**`ProfileController.java`**:

```java
package com.btree.api.user.profile;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de perfil do usuário autenticado.
 *
 * <p>Rotas protegidas — requer JWT válido no header {@code Authorization: Bearer <token>}.
 */
@RestController
@RequestMapping("/v1/users/me/profile")
@Tag(name = "Profile", description = "Gerenciamento do perfil do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final UpdateProfileUseCase updateProfileUseCase;

    public ProfileController(final UpdateProfileUseCase updateProfileUseCase) {
        this.updateProfileUseCase = updateProfileUseCase;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Atualizar perfil",
            description = "Edita os dados pessoais do usuário autenticado: nome, CPF, " +
                          "data de nascimento, idioma, moeda e preferência de newsletter. " +
                          "Todos os campos são opcionais — envie apenas o payload completo " +
                          "com os valores desejados (campos ausentes são gravados como null)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Formato de campo inválido (Bean Validation)"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "409", description = "Conflito de versão (optimistic locking) ou CPF já em uso"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public UpdateProfileResponse update(@Valid @RequestBody final UpdateProfileRequest request) {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final String userId = auth.getName();

        final var command = new UpdateProfileCommand(
                userId,
                request.firstName(),
                request.lastName(),
                request.cpf(),
                request.birthDate(),
                request.gender(),
                request.preferredLanguage(),
                request.preferredCurrency(),
                request.newsletterSubscribed()
        );

        return UpdateProfileResponse.from(
                updateProfileUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }
}
```

**`UseCaseConfig.java`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public UpdateProfileUseCase updateProfileUseCase(
        final ProfileGateway profileGateway,
        final TransactionManager transactionManager
) {
    return new UpdateProfileUseCase(profileGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `UserError.USER_NOT_FOUND` | `userId` nulo, vazio ou UUID inválido no JWT | `422 Unprocessable Entity` |
| `ProfileError.PROFILE_NOT_FOUND` | Perfil não existe para o `userId` informado | `422 Unprocessable Entity` |
| `ProfileError.CPF_ALREADY_IN_USE` | Outro usuário já possui o CPF fornecido | `422 Unprocessable Entity` |
| `ProfileError.CPF_INVALID` | CPF fornecido não passa na validação de dígitos verificadores | `422 Unprocessable Entity` |
| `ProfileError.NAME_TOO_LONG` | `firstName` ou `lastName` excede 100 caracteres | `400 Bad Request` (Bean Validation) |
| `ProfileError.PREFERRED_LANGUAGE_INVALID` | `preferredLanguage` fora do tamanho permitido | `422 Unprocessable Entity` |
| `ProfileError.PREFERRED_CURRENCY_INVALID` | `preferredCurrency` não tem exatamente 3 chars | `400 Bad Request` (Bean Validation) |
| `ObjectOptimisticLockingFailureException` | Duas requisições simultâneas editando o mesmo perfil | `409 Conflict` |
| `AuthenticationException` | JWT ausente ou inválido | `401 Unauthorized` |

---

## 🌐 Contrato da API REST

### Request

```http
PUT /api/v1/users/me/profile
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "first_name": "João",
  "last_name": "Silva",
  "cpf": "529.982.247-25",
  "birth_date": "1992-05-15",
  "gender": "male",
  "preferred_language": "pt-BR",
  "preferred_currency": "BRL",
  "newsletter_subscribed": true
}
```

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
  "updated_at": "2026-04-09T14:32:00Z"
}
```

### Response (Erro — 422 CPF já em uso)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "CPF já está em uso por outro usuário",
  "errors": ["CPF já está em uso por outro usuário"],
  "timestamp": "2026-04-09T14:32:00Z",
  "path": "/api/v1/users/me/profile"
}
```

### Response (Erro — 400 Bean Validation)

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "cpf: cpf deve estar no formato XXX.XXX.XXX-XX",
  "errors": [
    "cpf: cpf deve estar no formato XXX.XXX.XXX-XX",
    "preferred_currency: preferredCurrency deve ter exatamente 3 caracteres"
  ],
  "timestamp": "2026-04-09T14:32:00Z",
  "path": "/api/v1/users/me/profile"
}
```

### Response (Erro — 409 Optimistic Locking)

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "O recurso foi modificado por outra requisição. Tente novamente.",
  "timestamp": "2026-04-09T14:32:00Z",
  "path": "/api/v1/users/me/profile"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`ProfileError.java`** — adicionar constantes `CPF_ALREADY_IN_USE`, `PROFILE_NOT_FOUND`, `PREFERRED_LANGUAGE_INVALID`, `PREFERRED_CURRENCY_INVALID`.
2. **`ProfileValidator.java`** — criar validador com todas as regras de invariante.
3. **`Profile.java`** — adicionar `updatePersonalData(...)`, `buildDisplayName()` e sobrescrever `validate()` usando `ProfileValidator`.
4. **`ProfileGateway.java`** — adicionar `update(Profile)`, `findByUserId(UserId)` e `existsByCpfAndNotUserId(String, UserId)`.
5. **`UpdateProfileCommand.java`** — record com todos os campos editáveis.
6. **`UpdateProfileOutput.java`** — record com factory `from(Profile)`.
7. **`UpdateProfileUseCase.java`** — lógica completa com `Either`.
8. **`ProfileJpaRepository.java`** — adicionar `existsByCpfAndUserIdNot` e `findActiveByUserId`.
9. **`ProfilePostgresGateway.java`** — implementar `update`, `findByUserId` e `existsByCpfAndNotUserId`.
10. **`UseCaseConfig.java`** — registrar `@Bean` do `UpdateProfileUseCase`.
11. **`UpdateProfileRequest.java`** — record com Bean Validation e `@JsonProperty`.
12. **`UpdateProfileResponse.java`** — record com factory `from(UpdateProfileOutput)`.
13. **`ProfileController.java`** — controller com `PUT /v1/users/me/profile`.
14. **Testes unitários** — `UpdateProfileUseCaseTest` e `ProfileValidatorTest` em `application/` e `domain/` com Mockito.
15. **Testes de integração** — `ProfilePostgresGatewayIT` em `infrastructure/` com Testcontainers.

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `UpdateProfileUseCaseTest`

| Cenário | Comportamento esperado |
|---|---|
| Payload válido completo | `Right(UpdateProfileOutput)` com todos os campos atualizados |
| CPF válido único | `Right(UpdateProfileOutput)` — CPF persistido normalmente |
| CPF já em uso por outro usuário | `Left(Notification)` com `ProfileError.CPF_ALREADY_IN_USE` |
| CPF inválido (dígitos verificadores errados) | `Left(Notification)` com `ProfileError.CPF_INVALID` |
| Perfil não encontrado para o `userId` | `Left(Notification)` com `ProfileError.PROFILE_NOT_FOUND` |
| `userId` nulo | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` UUID inválido | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `firstName` com 101 caracteres | `Left(Notification)` com `ProfileError.NAME_TOO_LONG` |
| `preferredLanguage` com 1 caractere | `Left(Notification)` com `ProfileError.PREFERRED_LANGUAGE_INVALID` |
| `preferredCurrency` com 2 caracteres | `Left(Notification)` com `ProfileError.PREFERRED_CURRENCY_INVALID` |
| Todos os campos nulos (exceto `userId`) | `Right(UpdateProfileOutput)` — perfil com campos nulos |
| Gateway lança exceção de infra | `Left(Notification)` via `Try().toEither()` |

### Unitários (`domain/`) — `ProfileValidatorTest`

| Cenário | Comportamento esperado |
|---|---|
| Perfil com todos os campos válidos | Nenhum erro no `Notification` |
| CPF `"529.982.247-25"` | Aceito pelo validador |
| CPF `"000.000.000-00"` | `ProfileError.CPF_INVALID` |
| `firstName` com 100 chars | Aceito |
| `firstName` com 101 chars | `ProfileError.NAME_TOO_LONG` |
| `preferredLanguage = "pt-BR"` | Aceito |
| `preferredLanguage = "x"` | `ProfileError.PREFERRED_LANGUAGE_INVALID` |
| `preferredCurrency = "BRL"` | Aceito |
| `preferredCurrency = "BR"` | `ProfileError.PREFERRED_CURRENCY_INVALID` |
| Campos nulos (firstName, cpf, etc.) | Aceitos — campos opcionais |

### Integração (`infrastructure/`) — `ProfilePostgresGatewayIT`

| Cenário | Verificação |
|---|---|
| `update()` com dados novos | Persiste corretamente, `updated_at` maior que antes |
| `update()` muda `displayName` automaticamente | `display_name` = `firstName + " " + lastName` |
| `findByUserId()` para usuário com perfil | Retorna `Optional` com o perfil |
| `findByUserId()` para usuário sem perfil | Retorna `Optional.empty()` |
| `existsByCpfAndNotUserId()` — CPF de outro usuário | Retorna `true` |
| `existsByCpfAndNotUserId()` — CPF do próprio usuário | Retorna `false` |
| `existsByCpfAndNotUserId()` — CPF inexistente | Retorna `false` |
| Dois updates simultâneos (optimistic locking) | Um sucesso, outro lança `ObjectOptimisticLockingFailureException` |
| `update()` com perfil soft-deletado | `NotFoundException` (findById retorna o registro mas o gateway filtra) |