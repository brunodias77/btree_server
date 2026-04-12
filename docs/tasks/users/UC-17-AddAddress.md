# Task: UC-17 — AddAddress

## 📋 Resumo

Permite que o usuário autenticado cadastre um novo endereço de entrega em sua conta. Endereços são fundamentais para o fluxo de checkout — sem pelo menos um endereço cadastrado, o usuário não pode concluir uma compra. Suporta múltiplos endereços por usuário, com controle de endereço padrão e endereço de cobrança.

## 🎯 Objetivo

Implementar o endpoint `POST /api/v1/users/me/addresses` que recebe os dados de um endereço, valida formato de CEP, estado e campos obrigatórios, persiste na tabela `users.addresses` e retorna o endereço criado. Se `isDefault = true`, nenhum outro endereço do mesmo tipo (entrega/cobrança) pode permanecer como padrão — a lógica de troca de padrão é responsabilidade de use cases específicos (UC-20 SetDefaultAddress). Neste use case, o primeiro endereço cadastrado é automaticamente marcado como padrão.

## 📦 Contexto Técnico

- **Módulo Principal:** `application`
- **Prioridade:** `CRÍTICO`
- **Endpoint:** `POST /api/v1/users/me/addresses`
- **Tabelas do Banco:** `users.addresses`

---

## 🏗️ Arquivos a Criar / Alterar

### `domain`

1. `domain/src/main/java/com/btree/domain/user/gateway/AddressGateway.java` — **alterar**: adicionar métodos `save(Address)`, `countByUserId(UserId)` e revisar assinatura existente
2. `domain/src/main/java/com/btree/domain/user/entity/Address.java` — **alterar**: adicionar factory method `create(...)` completo com `Notification`, método `with(...)` para hidratação e regras de negócio
3. `domain/src/main/java/com/btree/domain/user/validator/AddressValidator.java` — **criar**: validador completo do aggregate `Address`
4. `domain/src/main/java/com/btree/domain/user/error/AddressError.java` — **alterar**: revisar e completar constantes de erro

### `application`

1. `application/src/main/java/com/btree/application/usecase/user/address/AddAddressCommand.java` — **criar**
2. `application/src/main/java/com/btree/application/usecase/user/address/AddAddressOutput.java` — **criar**
3. `application/src/main/java/com/btree/application/usecase/user/address/AddAddressUseCase.java` — **criar**

### `infrastructure`

1. `infrastructure/src/main/java/com/btree/infrastructure/user/entity/AddressJpaEntity.java` — **criar**
2. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/AddressJpaRepository.java` — **criar**
3. `infrastructure/src/main/java/com/btree/infrastructure/user/persistence/AddressPostgresGateway.java` — **criar**

### `api`

1. `api/src/main/java/com/btree/api/user/address/AddAddressRequest.java` — **criar**
2. `api/src/main/java/com/btree/api/user/address/AddressResponse.java` — **criar**
3. `api/src/main/java/com/btree/api/user/address/AddressController.java` — **criar**
4. `api/src/main/java/com/btree/api/config/UseCaseConfig.java` — **alterar**: registrar `@Bean`

---

## 📐 Algoritmo e Padrões de Implementação

### 1. Entidade e Validação (Domain)

**`AddressError.java`** — revisar e completar o arquivo existente:

```java
package com.btree.domain.user.error;

import com.btree.shared.validation.Error;

public final class AddressError {
    private AddressError() {}

    // Já existentes — manter
    public static final Error STREET_EMPTY       = new Error("'street' não pode estar vazio");
    public static final Error CITY_EMPTY         = new Error("'city' não pode estar vazia");
    public static final Error STATE_INVALID      = new Error("'state' deve conter 2 letras maiúsculas");
    public static final Error POSTAL_CODE_INVALID = new Error("O formato do 'postalCode' é inválido");

    // Adicionar
    public static final Error USER_ID_NULL       = new Error("'userId' não pode ser nulo");
    public static final Error COUNTRY_EMPTY      = new Error("'country' não pode estar vazio");
    public static final Error STREET_TOO_LONG    = new Error("'street' deve ter no máximo 255 caracteres");
    public static final Error CITY_TOO_LONG      = new Error("'city' deve ter no máximo 100 caracteres");
    public static final Error ADDRESS_NOT_FOUND  = new Error("Endereço não encontrado");
    public static final Error ADDRESS_ALREADY_DELETED = new Error("Endereço já foi removido");
}
```

**`AddressValidator.java`** — criar validador completo:

```java
package com.btree.domain.user.validator;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.error.AddressError;
import com.btree.shared.validation.ValidationHandler;
import com.btree.shared.validation.Validator;
import com.btree.shared.valueobject.PostalCode;

public class AddressValidator extends Validator {

    private static final int STREET_MAX_LENGTH = 255;
    private static final int CITY_MAX_LENGTH   = 100;
    private static final String STATE_REGEX    = "^[A-Z]{2}$";

    private final Address address;

    public AddressValidator(final Address address, final ValidationHandler handler) {
        super(handler);
        this.address = address;
    }

    @Override
    public void validate() {
        checkUserId();
        checkStreet();
        checkCity();
        checkState();
        checkPostalCode();
        checkCountry();
    }

    private void checkUserId() {
        if (address.getUserId() == null) {
            validationHandler().append(AddressError.USER_ID_NULL);
        }
    }

    private void checkStreet() {
        final var street = address.getStreet();
        if (street == null || street.isBlank()) {
            validationHandler().append(AddressError.STREET_EMPTY);
            return;
        }
        if (street.length() > STREET_MAX_LENGTH) {
            validationHandler().append(AddressError.STREET_TOO_LONG);
        }
    }

    private void checkCity() {
        final var city = address.getCity();
        if (city == null || city.isBlank()) {
            validationHandler().append(AddressError.CITY_EMPTY);
            return;
        }
        if (city.length() > CITY_MAX_LENGTH) {
            validationHandler().append(AddressError.CITY_TOO_LONG);
        }
    }

    private void checkState() {
        final var state = address.getState();
        if (state == null || !state.matches(STATE_REGEX)) {
            validationHandler().append(AddressError.STATE_INVALID);
        }
    }

    private void checkPostalCode() {
        final var postalCode = address.getPostalCode();
        if (postalCode == null || postalCode.isBlank()) {
            validationHandler().append(AddressError.POSTAL_CODE_INVALID);
            return;
        }
        try {
            PostalCode.of(postalCode);
        } catch (IllegalArgumentException e) {
            validationHandler().append(AddressError.POSTAL_CODE_INVALID);
        }
    }

    private void checkCountry() {
        final var country = address.getCountry();
        if (country == null || country.isBlank()) {
            validationHandler().append(AddressError.COUNTRY_EMPTY);
        }
    }
}
```

**`Address.java`** — revisão completa do aggregate existente, adicionando factory methods padronizados e `validate()` via `AddressValidator`:

```java
package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.validator.AddressValidator;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.math.BigDecimal;
import java.time.Instant;

public class Address extends Entity<AddressId> {

    private final UserId userId;
    private String label;
    private String recipientName;
    private String street;
    private String number;
    private String complement;
    private String neighborhood;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String ibgeCode;
    private boolean isDefault;
    private boolean isBillingAddress;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private Address(
            final AddressId id,
            final UserId userId,
            final String label,
            final String recipientName,
            final String street,
            final String number,
            final String complement,
            final String neighborhood,
            final String city,
            final String state,
            final String postalCode,
            final String country,
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String ibgeCode,
            final boolean isDefault,
            final boolean isBillingAddress,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        super(id);
        this.userId           = userId;
        this.label            = label;
        this.recipientName    = recipientName;
        this.street           = street;
        this.number           = number;
        this.complement       = complement;
        this.neighborhood     = neighborhood;
        this.city             = city;
        this.state            = state;
        this.postalCode       = postalCode;
        this.country          = country;
        this.latitude         = latitude;
        this.longitude        = longitude;
        this.ibgeCode         = ibgeCode;
        this.isDefault        = isDefault;
        this.isBillingAddress = isBillingAddress;
        this.createdAt        = createdAt;
        this.updatedAt        = updatedAt;
        this.deletedAt        = deletedAt;
    }

    /**
     * Factory para criação de novo endereço.
     *
     * <p>Acumula erros de validação no {@code notification} passado —
     * não lança exceção diretamente. O chamador verifica
     * {@code notification.hasError()} após a chamada.
     *
     * @param isFirstAddress quando {@code true}, o endereço é automaticamente
     *                       marcado como padrão (primeiro endereço do usuário)
     */
    public static Address create(
            final UserId userId,
            final String label,
            final String recipientName,
            final String street,
            final String number,
            final String complement,
            final String neighborhood,
            final String city,
            final String state,
            final String postalCode,
            final String country,
            final boolean isBillingAddress,
            final boolean isFirstAddress,
            final Notification notification
    ) {
        final var now = Instant.now();
        final var address = new Address(
                AddressId.unique(),
                userId,
                label,
                recipientName,
                street,
                number,
                complement,
                neighborhood,
                city,
                state,
                postalCode,
                country != null ? country : "BR",
                null,
                null,
                null,
                isFirstAddress, // primeiro endereço é automaticamente padrão
                isBillingAddress,
                now,
                now,
                null
        );
        address.validate(notification);
        return address;
    }

    /**
     * Factory para hidratação a partir do banco (reconstrutor).
     * Não valida — assume que os dados persistidos são consistentes.
     */
    public static Address with(
            final AddressId id,
            final UserId userId,
            final String label,
            final String recipientName,
            final String street,
            final String number,
            final String complement,
            final String neighborhood,
            final String city,
            final String state,
            final String postalCode,
            final String country,
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String ibgeCode,
            final boolean isDefault,
            final boolean isBillingAddress,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant deletedAt
    ) {
        return new Address(
                id, userId, label, recipientName, street, number,
                complement, neighborhood, city, state, postalCode,
                country, latitude, longitude, ibgeCode,
                isDefault, isBillingAddress,
                createdAt, updatedAt, deletedAt
        );
    }

    // ── Comportamentos de domínio ─────────────────────────────────────────

    public void setAsDefault() {
        this.isDefault = true;
        this.updatedAt = Instant.now();
    }

    public void unsetDefault() {
        this.isDefault = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    // ── Validação ────────────────────────────────────────────────────────

    @Override
    public void validate(final ValidationHandler handler) {
        new AddressValidator(this, handler).validate();
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UserId getUserId()           { return userId; }
    public String getLabel()            { return label; }
    public String getRecipientName()    { return recipientName; }
    public String getStreet()           { return street; }
    public String getNumber()           { return number; }
    public String getComplement()       { return complement; }
    public String getNeighborhood()     { return neighborhood; }
    public String getCity()             { return city; }
    public String getState()            { return state; }
    public String getPostalCode()       { return postalCode; }
    public String getCountry()          { return country; }
    public BigDecimal getLatitude()     { return latitude; }
    public BigDecimal getLongitude()    { return longitude; }
    public String getIbgeCode()         { return ibgeCode; }
    public boolean isDefault()          { return isDefault; }
    public boolean isBillingAddress()   { return isBillingAddress; }
    public Instant getCreatedAt()       { return createdAt; }
    public Instant getUpdatedAt()       { return updatedAt; }
    public Instant getDeletedAt()       { return deletedAt; }
}
```

**`AddressGateway.java`** — revisar e completar:

```java
package com.btree.domain.user.gateway;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;

import java.util.List;
import java.util.Optional;

public interface AddressGateway {

    /** Persiste um novo endereço. */
    Address save(Address address);

    /** Atualiza um endereço existente (ex: marcar/desmarcar como padrão). */
    Address update(Address address);

    /** Busca endereço pelo ID, incluindo soft-deletados. */
    Optional<Address> findById(AddressId id);

    /** Lista endereços ativos do usuário (excluindo soft-deletados). */
    List<Address> findByUserId(UserId userId);

    /**
     * Conta endereços ativos do usuário.
     * Usado para determinar se o novo endereço deve ser automaticamente padrão.
     */
    long countActiveByUserId(UserId userId);

    /**
     * Remove a marcação de padrão de todos os endereços de entrega do usuário.
     * Chamado antes de marcar um novo endereço como padrão.
     */
    void clearDefaultByUserId(UserId userId);
}
```

### 2. Contrato de Entrada/Saída (Application)

**`AddAddressCommand.java`**:

```java
package com.btree.application.usecase.user.address;

/**
 * Comando de entrada para UC-17 — AddAddress.
 *
 * @param userId          ID do usuário autenticado (extraído do JWT)
 * @param label           rótulo do endereço (ex: "Casa", "Trabalho")
 * @param recipientName   nome do destinatário na entrega
 * @param street          logradouro (obrigatório)
 * @param number          número
 * @param complement      complemento (apto, bloco, etc.)
 * @param neighborhood    bairro
 * @param city            cidade (obrigatório)
 * @param state           UF com 2 letras maiúsculas (obrigatório)
 * @param postalCode      CEP no formato XXXXX-XXX ou XXXXXXXX (obrigatório)
 * @param country         país ISO-2 (padrão: "BR")
 * @param isBillingAddress se é endereço de cobrança
 */
public record AddAddressCommand(
        String userId,
        String label,
        String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isBillingAddress
) {}
```

**`AddAddressOutput.java`**:

```java
package com.btree.application.usecase.user.address;

import com.btree.domain.user.entity.Address;

import java.time.Instant;

/**
 * Saída do caso de uso UC-17 — AddAddress.
 */
public record AddAddressOutput(
        String id,
        String userId,
        String label,
        String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault,
        boolean isBillingAddress,
        Instant createdAt
) {
    public static AddAddressOutput from(final Address address) {
        return new AddAddressOutput(
                address.getId().getValue().toString(),
                address.getUserId().getValue().toString(),
                address.getLabel(),
                address.getRecipientName(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault(),
                address.isBillingAddress(),
                address.getCreatedAt()
        );
    }
}
```

### 3. Lógica do Use Case (Application)

```java
package com.btree.application.usecase.user.address;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.error.UserError;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.UserId;
import com.btree.shared.contract.TransactionManager;
import com.btree.shared.usecase.UseCase;
import com.btree.shared.validation.Notification;
import io.vavr.control.Either;

import java.util.UUID;

import static io.vavr.API.Left;
import static io.vavr.API.Try;

/**
 * Caso de uso UC-17 — AddAddress [CMD P0].
 *
 * <p>Cadastra um novo endereço de entrega para o usuário autenticado.
 *
 * <p>Regra de negócio — endereço padrão automático:
 * Se este for o primeiro endereço do usuário ({@code countActiveByUserId = 0}),
 * ele é automaticamente marcado como padrão ({@code isDefault = true}),
 * garantindo que o usuário sempre tenha um endereço padrão disponível
 * para o checkout sem necessidade de ação adicional.
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>Valida presença e formato do {@code userId}.</li>
 *   <li>Verifica se é o primeiro endereço do usuário.</li>
 *   <li>Cria o aggregate {@link Address} via factory method.</li>
 *   <li>Valida invariantes via {@code AddressValidator}.</li>
 *   <li>Persiste dentro da transação.</li>
 *   <li>Retorna {@link AddAddressOutput} com o endereço criado.</li>
 * </ol>
 */
public class AddAddressUseCase implements UseCase<AddAddressCommand, AddAddressOutput> {

    private final AddressGateway addressGateway;
    private final TransactionManager transactionManager;

    public AddAddressUseCase(
            final AddressGateway addressGateway,
            final TransactionManager transactionManager
    ) {
        this.addressGateway = addressGateway;
        this.transactionManager = transactionManager;
    }

    @Override
    public Either<Notification, AddAddressOutput> execute(final AddAddressCommand command) {
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

        // 2. Verificar se é o primeiro endereço (determina isDefault automático)
        final boolean isFirstAddress = addressGateway.countActiveByUserId(userId) == 0;

        // 3. Criar aggregate com validação acumulada
        final var address = Address.create(
                userId,
                command.label(),
                command.recipientName(),
                command.street(),
                command.number(),
                command.complement(),
                command.neighborhood(),
                command.city(),
                command.state(),
                command.postalCode(),
                command.country(),
                command.isBillingAddress(),
                isFirstAddress,
                notification
        );

        if (notification.hasError()) {
            return Left(notification);
        }

        // 4. Persistir dentro da transação
        return Try(() -> transactionManager.execute(() -> {
            final var saved = addressGateway.save(address);
            return AddAddressOutput.from(saved);
        })).toEither().mapLeft(Notification::create);
    }
}
```

### 4. Persistência (Infrastructure)

**`AddressJpaEntity.java`**:

```java
package com.btree.infrastructure.user.entity;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity para {@code users.addresses}.
 *
 * <p>Suporta soft-delete via {@code deleted_at}.
 * Sem {@code @Version} — endereços não têm optimistic locking
 * (schema não possui coluna {@code version}).
 */
@Entity
@Table(name = "addresses", schema = "users")
public class AddressJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(name = "street", nullable = false, length = 255)
    private String street;

    @Column(name = "number", length = 20)
    private String number;

    @Column(name = "complement", length = 100)
    private String complement;

    @Column(name = "neighborhood", length = 100)
    private String neighborhood;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 2)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 9)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "ibge_code", length = 7)
    private String ibgeCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_billing_address", nullable = false)
    private boolean isBillingAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public AddressJpaEntity() {}

    public static AddressJpaEntity from(final Address address) {
        final var entity = new AddressJpaEntity();
        entity.id               = address.getId().getValue();
        entity.userId           = address.getUserId().getValue();
        entity.label            = address.getLabel();
        entity.recipientName    = address.getRecipientName();
        entity.street           = address.getStreet();
        entity.number           = address.getNumber();
        entity.complement       = address.getComplement();
        entity.neighborhood     = address.getNeighborhood();
        entity.city             = address.getCity();
        entity.state            = address.getState();
        entity.postalCode       = address.getPostalCode();
        entity.country          = address.getCountry();
        entity.latitude         = address.getLatitude();
        entity.longitude        = address.getLongitude();
        entity.ibgeCode         = address.getIbgeCode();
        entity.isDefault        = address.isDefault();
        entity.isBillingAddress = address.isBillingAddress();
        entity.createdAt        = address.getCreatedAt();
        entity.updatedAt        = address.getUpdatedAt();
        entity.deletedAt        = address.getDeletedAt();
        return entity;
    }

    public Address toAggregate() {
        return Address.with(
                AddressId.from(this.id),
                UserId.from(this.userId),
                this.label,
                this.recipientName,
                this.street,
                this.number,
                this.complement,
                this.neighborhood,
                this.city,
                this.state,
                this.postalCode,
                this.country,
                this.latitude,
                this.longitude,
                this.ibgeCode,
                this.isDefault,
                this.isBillingAddress,
                this.createdAt,
                this.updatedAt,
                this.deletedAt
        );
    }

    /**
     * Atualiza campos mutáveis preservando {@code id}, {@code userId} e {@code createdAt}.
     */
    public void updateFrom(final Address address) {
        this.label            = address.getLabel();
        this.recipientName    = address.getRecipientName();
        this.street           = address.getStreet();
        this.number           = address.getNumber();
        this.complement       = address.getComplement();
        this.neighborhood     = address.getNeighborhood();
        this.city             = address.getCity();
        this.state            = address.getState();
        this.postalCode       = address.getPostalCode();
        this.country          = address.getCountry();
        this.latitude         = address.getLatitude();
        this.longitude        = address.getLongitude();
        this.ibgeCode         = address.getIbgeCode();
        this.isDefault        = address.isDefault();
        this.isBillingAddress = address.isBillingAddress();
        this.updatedAt        = address.getUpdatedAt();
        this.deletedAt        = address.getDeletedAt();
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getId()             { return id; }
    public UUID getUserId()         { return userId; }
    public String getLabel()        { return label; }
    public String getStreet()       { return street; }
    public String getCity()         { return city; }
    public String getState()        { return state; }
    public String getPostalCode()   { return postalCode; }
    public boolean isDefault()      { return isDefault; }
    public boolean isBillingAddress() { return isBillingAddress; }
    public Instant getDeletedAt()   { return deletedAt; }
}
```

**`AddressJpaRepository.java`**:

```java
package com.btree.infrastructure.user.persistence;

import com.btree.infrastructure.user.entity.AddressJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, UUID> {

    /**
     * Lista endereços ativos do usuário (sem soft-delete).
     */
    @Query("""
        SELECT a FROM AddressJpaEntity a
        WHERE a.userId = :userId
          AND a.deletedAt IS NULL
        ORDER BY a.createdAt ASC
        """)
    List<AddressJpaEntity> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Conta endereços ativos do usuário.
     * Usado para determinar se o novo endereço deve ser padrão automático.
     */
    @Query("""
        SELECT COUNT(a) FROM AddressJpaEntity a
        WHERE a.userId = :userId
          AND a.deletedAt IS NULL
        """)
    long countActiveByUserId(@Param("userId") UUID userId);

    /**
     * Remove a marcação de padrão de todos os endereços de entrega do usuário.
     * Chamado antes de definir um novo endereço como padrão (UC-20).
     */
    @Modifying
    @Query("""
        UPDATE AddressJpaEntity a
        SET a.isDefault = false,
            a.updatedAt = :now
        WHERE a.userId = :userId
          AND a.isDefault = true
          AND a.deletedAt IS NULL
        """)
    int clearDefaultByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
```

**`AddressPostgresGateway.java`**:

```java
package com.btree.infrastructure.user.persistence;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.gateway.AddressGateway;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.user.entity.AddressJpaEntity;
import com.btree.shared.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class AddressPostgresGateway implements AddressGateway {

    private final AddressJpaRepository addressJpaRepository;

    public AddressPostgresGateway(final AddressJpaRepository addressJpaRepository) {
        this.addressJpaRepository = addressJpaRepository;
    }

    @Override
    public Address save(final Address address) {
        return addressJpaRepository
                .save(AddressJpaEntity.from(address))
                .toAggregate();
    }

    @Override
    public Address update(final Address address) {
        final var entity = addressJpaRepository.findById(address.getId().getValue())
                .orElseThrow(() -> NotFoundException.with(
                        "Endereço não encontrado: " + address.getId().getValue()));
        entity.updateFrom(address);
        return addressJpaRepository.save(entity).toAggregate();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Address> findById(final AddressId id) {
        return addressJpaRepository
                .findById(id.getValue())
                .map(AddressJpaEntity::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Address> findByUserId(final UserId userId) {
        return addressJpaRepository
                .findActiveByUserId(userId.getValue())
                .stream()
                .map(AddressJpaEntity::toAggregate)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveByUserId(final UserId userId) {
        return addressJpaRepository.countActiveByUserId(userId.getValue());
    }

    @Override
    public void clearDefaultByUserId(final UserId userId) {
        addressJpaRepository.clearDefaultByUserId(userId.getValue(), Instant.now());
    }
}
```

### 5. Roteamento e Injeção (API)

**`AddAddressRequest.java`**:

```java
package com.btree.api.user.address;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO HTTP de entrada para {@code POST /api/v1/users/me/addresses}.
 */
public record AddAddressRequest(

        @Size(max = 50, message = "label deve ter no máximo 50 caracteres")
        String label,

        @Size(max = 150, message = "recipientName deve ter no máximo 150 caracteres")
        @JsonProperty("recipient_name")
        String recipientName,

        @NotBlank(message = "street é obrigatório")
        @Size(max = 255, message = "street deve ter no máximo 255 caracteres")
        String street,

        @Size(max = 20, message = "number deve ter no máximo 20 caracteres")
        String number,

        @Size(max = 100, message = "complement deve ter no máximo 100 caracteres")
        String complement,

        @Size(max = 100, message = "neighborhood deve ter no máximo 100 caracteres")
        String neighborhood,

        @NotBlank(message = "city é obrigatório")
        @Size(max = 100, message = "city deve ter no máximo 100 caracteres")
        String city,

        @NotBlank(message = "state é obrigatório")
        @Pattern(regexp = "^[A-Z]{2}$", message = "state deve conter exatamente 2 letras maiúsculas")
        String state,

        @NotBlank(message = "postalCode é obrigatório")
        @Pattern(
            regexp = "^\\d{5}-?\\d{3}$",
            message = "postalCode deve estar no formato XXXXX-XXX ou XXXXXXXX"
        )
        @JsonProperty("postal_code")
        String postalCode,

        @Size(min = 2, max = 2, message = "country deve ter exatamente 2 caracteres")
        String country,

        @JsonProperty("is_billing_address")
        boolean isBillingAddress
) {}
```

**`AddressResponse.java`**:

```java
package com.btree.api.user.address;

import com.btree.application.usecase.user.address.AddAddressOutput;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO HTTP de saída — representa um endereço.
 * Usado como resposta do {@code POST} (UC-17) e reutilizado
 * nos demais endpoints do {@code AddressController}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressResponse(
        String id,
        @JsonProperty("user_id")          String userId,
        String label,
        @JsonProperty("recipient_name")   String recipientName,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        @JsonProperty("postal_code")      String postalCode,
        String country,
        @JsonProperty("is_default")       boolean isDefault,
        @JsonProperty("is_billing_address") boolean isBillingAddress,
        @JsonProperty("created_at")       Instant createdAt
) {
    public static AddressResponse from(final AddAddressOutput output) {
        return new AddressResponse(
                output.id(),
                output.userId(),
                output.label(),
                output.recipientName(),
                output.street(),
                output.number(),
                output.complement(),
                output.neighborhood(),
                output.city(),
                output.state(),
                output.postalCode(),
                output.country(),
                output.isDefault(),
                output.isBillingAddress(),
                output.createdAt()
        );
    }
}
```

**`AddressController.java`**:

```java
package com.btree.api.user.address;

import com.btree.application.usecase.user.address.AddAddressCommand;
import com.btree.application.usecase.user.address.AddAddressUseCase;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de endereços do usuário autenticado.
 *
 * <p>Rotas protegidas — requer JWT válido no header
 * {@code Authorization: Bearer <token>}.
 *
 * <p>Use cases mapeados:
 * <ul>
 *   <li>UC-17 AddAddress  — {@code POST /}</li>
 *   <li>UC-18 UpdateAddress — {@code PUT /{id}} (próximo use case)</li>
 *   <li>UC-19 DeleteAddress — {@code DELETE /{id}} (próximo use case)</li>
 *   <li>UC-20 SetDefaultAddress — {@code PATCH /{id}/default} (próximo use case)</li>
 *   <li>UC-21 ListAddresses — {@code GET /} (próximo use case)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/users/me/addresses")
@Tag(name = "Addresses", description = "Gerenciamento de endereços de entrega do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {

    private final AddAddressUseCase addAddressUseCase;

    public AddressController(final AddAddressUseCase addAddressUseCase) {
        this.addAddressUseCase = addAddressUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar endereço",
            description = "Adiciona um novo endereço de entrega à conta do usuário autenticado. " +
                          "Se for o primeiro endereço, é automaticamente marcado como padrão."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Endereço cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos (formato de CEP, UF, etc.)"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "422", description = "Regras de domínio violadas")
    })
    public AddressResponse add(@Valid @RequestBody final AddAddressRequest request) {
        final String userId = currentUserId();

        final var command = new AddAddressCommand(
                userId,
                request.label(),
                request.recipientName(),
                request.street(),
                request.number(),
                request.complement(),
                request.neighborhood(),
                request.city(),
                request.state(),
                request.postalCode(),
                request.country() != null ? request.country() : "BR",
                request.isBillingAddress()
        );

        return AddressResponse.from(
                addAddressUseCase.execute(command)
                        .getOrElseThrow(n -> DomainException.with(n.getErrors()))
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String currentUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
```

**`UseCaseConfig.java`** — registrar o bean:

```java
// UseCaseConfig.java — adicionar

@Bean
public AddAddressUseCase addAddressUseCase(
        final AddressGateway addressGateway,
        final TransactionManager transactionManager
) {
    return new AddAddressUseCase(addressGateway, transactionManager);
}
```

---

## ⚠️ Casos de Erro Mapeados

| Erro de Domínio | Condição | Status HTTP Resultante |
|---|---|---|
| `UserError.USER_NOT_FOUND` | `userId` nulo, vazio ou UUID inválido no JWT | `422 Unprocessable Entity` |
| `AddressError.STREET_EMPTY` | Campo `street` nulo ou em branco | `400 Bad Request` (Bean Validation) |
| `AddressError.STREET_TOO_LONG` | `street` com mais de 255 chars | `400 Bad Request` (Bean Validation) |
| `AddressError.CITY_EMPTY` | Campo `city` nulo ou em branco | `400 Bad Request` (Bean Validation) |
| `AddressError.STATE_INVALID` | `state` não corresponde a 2 letras maiúsculas | `400 Bad Request` (Bean Validation) |
| `AddressError.POSTAL_CODE_INVALID` | CEP fora do formato `XXXXX-XXX` ou `XXXXXXXX` | `400 Bad Request` (Bean Validation) |
| `AddressError.USER_ID_NULL` | `userId` nulo no aggregate (proteção de invariante) | `422 Unprocessable Entity` |
| `AuthenticationException` | JWT ausente ou inválido | `401 Unauthorized` |

> **Dupla barreira de validação:** campos obrigatórios como `street`, `city`, `state` e `postalCode` são validados pela Bean Validation (`@NotBlank`, `@Pattern`) no `AddAddressRequest` antes de chegar ao use case. O `AddressValidator` no domínio serve como segunda barreira e garante que a entidade nunca persiste em estado inválido, independentemente de por onde seja criada.

---

## 🌐 Contrato da API REST

### Request

```http
POST /api/v1/users/me/addresses
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "label": "Casa",
  "recipient_name": "João Silva",
  "street": "Rua das Flores",
  "number": "123",
  "complement": "Apto 42",
  "neighborhood": "Jardim Paulista",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_billing_address": false
}
```

### Response (Sucesso — 201 Created)

```json
{
  "id": "019486ab-c123-7def-a456-789012345678",
  "user_id": "019486ab-c123-7def-a456-789012345679",
  "label": "Casa",
  "recipient_name": "João Silva",
  "street": "Rua das Flores",
  "number": "123",
  "complement": "Apto 42",
  "neighborhood": "Jardim Paulista",
  "city": "São Paulo",
  "state": "SP",
  "postal_code": "01310-100",
  "country": "BR",
  "is_default": true,
  "is_billing_address": false,
  "created_at": "2026-04-09T14:32:00Z"
}
```

> `"is_default": true` porque este é o primeiro endereço do usuário.

### Response (Erro — 400 Bean Validation)

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "state: state deve conter exatamente 2 letras maiúsculas",
  "errors": [
    "state: state deve conter exatamente 2 letras maiúsculas",
    "postal_code: postalCode deve estar no formato XXXXX-XXX ou XXXXXXXX"
  ],
  "timestamp": "2026-04-09T14:32:00Z",
  "path": "/api/v1/users/me/addresses"
}
```

### Response (Erro — 422 Regra de domínio)

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Usuário não encontrado",
  "errors": ["Usuário não encontrado"],
  "timestamp": "2026-04-09T14:32:00Z",
  "path": "/api/v1/users/me/addresses"
}
```

---

## 📋 Ordem de Desenvolvimento Sugerida

1. **`AddressError.java`** — adicionar constantes `USER_ID_NULL`, `COUNTRY_EMPTY`, `STREET_TOO_LONG`, `CITY_TOO_LONG`, `ADDRESS_NOT_FOUND`, `ADDRESS_ALREADY_DELETED`.
2. **`AddressValidator.java`** — criar com todas as regras de invariante do aggregate.
3. **`Address.java`** — refatorar: adicionar `create(...)` com `Notification`, `with(...)` completo, `softDelete()`, `isDeleted()` e sobrescrever `validate()` usando `AddressValidator`.
4. **`AddressGateway.java`** — adicionar `save`, `update`, `findById`, `findByUserId`, `countActiveByUserId`, `clearDefaultByUserId`.
5. **`AddAddressCommand.java`** — record com todos os campos de entrada.
6. **`AddAddressOutput.java`** — record com factory `from(Address)`.
7. **`AddAddressUseCase.java`** — lógica com `Either`, regra de primeiro endereço automático.
8. **`AddressJpaEntity.java`** — entity JPA com `from`, `toAggregate` e `updateFrom`.
9. **`AddressJpaRepository.java`** — queries `findActiveByUserId`, `countActiveByUserId` e `clearDefaultByUserId`.
10. **`AddressPostgresGateway.java`** — implementar todos os métodos do gateway.
11. **`UseCaseConfig.java`** — registrar `@Bean` do `AddAddressUseCase`.
12. **`AddAddressRequest.java`** — record com Bean Validation.
13. **`AddressResponse.java`** — record reutilizável com `@JsonInclude(NON_NULL)`.
14. **`AddressController.java`** — controller base com endpoint `POST`.
15. **Testes unitários** — `AddAddressUseCaseTest` e `AddressValidatorTest`.
16. **Testes de integração** — `AddressPostgresGatewayIT` com Testcontainers.

---

## 🧪 Cenários de Teste

### Unitários (`application/`) — `AddAddressUseCaseTest`

| Cenário | Comportamento esperado |
|---|---|
| Payload válido — primeiro endereço do usuário | `Right(AddAddressOutput)` com `isDefault = true` |
| Payload válido — usuário já tem outros endereços | `Right(AddAddressOutput)` com `isDefault = false` |
| `userId` nulo | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` em branco | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `userId` UUID inválido | `Left(Notification)` com `UserError.USER_NOT_FOUND` |
| `street` nulo | `Left(Notification)` com `AddressError.STREET_EMPTY` |
| `state` com formato inválido (`"São Paulo"`) | `Left(Notification)` com `AddressError.STATE_INVALID` |
| `postalCode` com formato inválido (`"1234-567"`) | `Left(Notification)` com `AddressError.POSTAL_CODE_INVALID` |
| Múltiplos campos inválidos simultaneamente | `Left(Notification)` com todos os erros acumulados |
| `country` nulo | Padrão `"BR"` aplicado automaticamente |
| Gateway lança exceção de infra | `Left(Notification)` via `Try().toEither()` |

### Unitários (`domain/`) — `AddressValidatorTest`

| Cenário | Comportamento esperado |
|---|---|
| Endereço válido completo | Nenhum erro |
| `street` vazio (`""`) | `AddressError.STREET_EMPTY` |
| `street` com 256 chars | `AddressError.STREET_TOO_LONG` |
| `city` vazia | `AddressError.CITY_EMPTY` |
| `state = "sp"` (minúsculas) | `AddressError.STATE_INVALID` |
| `state = "SPP"` (3 letras) | `AddressError.STATE_INVALID` |
| `postalCode = "01310-100"` | Aceito |
| `postalCode = "01310100"` | Aceito |
| `postalCode = "1234-567"` | `AddressError.POSTAL_CODE_INVALID` |
| `userId` nulo | `AddressError.USER_ID_NULL` |
| `country` nulo | `AddressError.COUNTRY_EMPTY` |

### Integração (`infrastructure/`) — `AddressPostgresGatewayIT`

| Cenário | Verificação |
|---|---|
| `save()` com dados válidos | Persiste e retorna aggregate com mesmo ID |
| `countActiveByUserId()` sem endereços | Retorna `0` |
| `countActiveByUserId()` com 3 endereços ativos e 1 deletado | Retorna `3` |
| `findByUserId()` filtra soft-deletados | Retorna apenas registros com `deleted_at IS NULL` |
| `findByUserId()` ordenado por `createdAt ASC` | Primeiro endereço cadastrado aparece primeiro |
| `update()` com `isDefault = false` | Persiste `is_default = false` sem alterar `id` ou `created_at` |
| `clearDefaultByUserId()` com 2 endereços padrão | Ambos ficam com `is_default = false` |
| `clearDefaultByUserId()` sem endereços padrão | Retorna `0`, sem exceção |
| `findById()` para endereço soft-deletado | Retorna `Optional` com o registro (sem filtro de deleted) |
| Primeiro endereço salvo com `isDefault = true` | Campo `is_default = true` no banco |
| Segundo endereço salvo com `isDefault = false` | Campo `is_default = false` no banco |