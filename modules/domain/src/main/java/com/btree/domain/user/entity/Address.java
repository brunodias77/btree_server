package com.btree.domain.user.entity;

import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.validator.AddressValidator;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity — maps to {@code users.addresses} table.
 * Supports soft delete and one default address per user.
 */
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
                isFirstAddress,
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

    public void updateData(
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
            final boolean isBillingAddress
    ) {
        this.label            = label;
        this.recipientName    = recipientName;
        this.street           = street;
        this.number           = number;
        this.complement       = complement;
        this.neighborhood     = neighborhood;
        this.city             = city;
        this.state            = state;
        this.postalCode       = postalCode;
        this.country          = country != null ? country : "BR";
        this.isBillingAddress = isBillingAddress;
        this.updatedAt        = Instant.now();
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

