package com.btree.infrastructure.user.entity;

import com.btree.domain.user.entity.Address;
import com.btree.domain.user.identifier.AddressId;
import com.btree.domain.user.identifier.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses", schema = "users")
public class AddressJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserJpaEntity user;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "street", nullable = false, length = 256)
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

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
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

    public static AddressJpaEntity from(final Address address, final UserJpaEntity user) {
        final var entity = new AddressJpaEntity();
        entity.setId(address.getId().getValue());
        entity.setUser(user);
        entity.setLabel(address.getLabel());
        entity.setRecipientName(address.getRecipientName());
        entity.setStreet(address.getStreet());
        entity.setNumber(address.getNumber());
        entity.setComplement(address.getComplement());
        entity.setNeighborhood(address.getNeighborhood());
        entity.setCity(address.getCity());
        entity.setState(address.getState());
        entity.setPostalCode(address.getPostalCode());
        entity.setCountry(address.getCountry());
        entity.setLatitude(address.getLatitude());
        entity.setLongitude(address.getLongitude());
        entity.setIbgeCode(address.getIbgeCode());
        entity.setDefault(address.isDefault());
        entity.setBillingAddress(address.isBillingAddress());
        entity.setCreatedAt(address.getCreatedAt());
        entity.setUpdatedAt(address.getUpdatedAt());
        entity.setDeletedAt(address.getDeletedAt());
        return entity;
    }

    public Address toAggregate() {
        return Address.with(
                AddressId.from(this.id),
                UserId.from(this.user.getId()),
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

    // ── Getters & Setters ─────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UserJpaEntity getUser() { return user; }
    public void setUser(UserJpaEntity user) { this.user = user; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getComplement() { return complement; }
    public void setComplement(String complement) { this.complement = complement; }

    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public String getIbgeCode() { return ibgeCode; }
    public void setIbgeCode(String ibgeCode) { this.ibgeCode = ibgeCode; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public boolean isBillingAddress() { return isBillingAddress; }
    public void setBillingAddress(boolean isBillingAddress) { this.isBillingAddress = isBillingAddress; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
