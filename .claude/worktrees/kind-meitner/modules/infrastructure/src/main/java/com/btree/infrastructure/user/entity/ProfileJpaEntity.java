package com.btree.infrastructure.user.entity;

import com.btree.domain.user.entity.Profile;
import com.btree.domain.user.identifier.ProfileId;
import com.btree.domain.user.identifier.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "profiles", schema = "users")
public class ProfileJpaEntity {

    @Id
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private UserJpaEntity user;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "cpf", length = 14, unique = true)
    private String cpf;

    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage;

    @Column(name = "preferred_currency", length = 3)
    private String preferredCurrency;

    @Column(name = "newsletter_subscribed")
    private boolean newsletterSubscribed;

    @Column(name = "accepted_terms_at")
    private Instant acceptedTermsAt;

    @Column(name = "accepted_privacy_at")
    private Instant acceptedPrivacyAt;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ProfileJpaEntity() {
    }

    public static ProfileJpaEntity from(final Profile profile, final UserJpaEntity user) {
        if (profile == null) return null;
        var entity = new ProfileJpaEntity();
        entity.setId(profile.getId().getValue());
        entity.setUser(user);
        entity.setFirstName(profile.getFirstName());
        entity.setLastName(profile.getLastName());
        entity.setDisplayName(profile.getDisplayName());
        entity.setAvatarUrl(profile.getAvatarUrl());
        entity.setBirthDate(profile.getBirthDate());
        entity.setGender(profile.getGender());
        entity.setCpf(profile.getCpf());
        entity.setPreferredLanguage(profile.getPreferredLanguage());
        entity.setPreferredCurrency(profile.getPreferredCurrency());
        entity.setNewsletterSubscribed(profile.isNewsletterSubscribed());
        entity.setAcceptedTermsAt(profile.getAcceptedTermsAt());
        entity.setAcceptedPrivacyAt(profile.getAcceptedPrivacyAt());
        entity.setCreatedAt(profile.getCreatedAt());
        entity.setUpdatedAt(profile.getUpdatedAt());
        entity.setDeletedAt(profile.getDeletedAt());
        return entity;
    }

    public Profile toAggregate() {
        return Profile.with(
                ProfileId.from(this.id),
                UserId.from(this.user.getId()),
                this.firstName, this.lastName, this.displayName, this.avatarUrl,
                this.birthDate, this.gender, this.cpf, this.preferredLanguage,
                this.preferredCurrency, this.newsletterSubscribed, this.acceptedTermsAt,
                this.acceptedPrivacyAt, this.createdAt, this.updatedAt,
                this.deletedAt
        );
    }

    /**
     * Atualiza campos mutáveis preservando {@code id}, {@code user} e {@code version}
     * gerenciados pelo Hibernate. Usar apenas a partir de {@link com.btree.infrastructure.user.entity.UserJpaEntity#updateFrom}.
     */
    public void updateFrom(final Profile profile) {
        this.firstName = profile.getFirstName();
        this.lastName = profile.getLastName();
        this.displayName = profile.getDisplayName();
        this.avatarUrl = profile.getAvatarUrl();
        this.birthDate = profile.getBirthDate();
        this.gender = profile.getGender();
        this.cpf = profile.getCpf();
        this.preferredLanguage = profile.getPreferredLanguage();
        this.preferredCurrency = profile.getPreferredCurrency();
        this.newsletterSubscribed = profile.isNewsletterSubscribed();
        this.acceptedTermsAt = profile.getAcceptedTermsAt();
        this.acceptedPrivacyAt = profile.getAcceptedPrivacyAt();
        this.updatedAt = profile.getUpdatedAt();
        this.deletedAt = profile.getDeletedAt();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UserJpaEntity getUser() { return user; }
    public void setUser(UserJpaEntity user) { this.user = user; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }
    public boolean isNewsletterSubscribed() { return newsletterSubscribed; }
    public void setNewsletterSubscribed(boolean newsletterSubscribed) { this.newsletterSubscribed = newsletterSubscribed; }
    public Instant getAcceptedTermsAt() { return acceptedTermsAt; }
    public void setAcceptedTermsAt(Instant acceptedTermsAt) { this.acceptedTermsAt = acceptedTermsAt; }
    public Instant getAcceptedPrivacyAt() { return acceptedPrivacyAt; }
    public void setAcceptedPrivacyAt(Instant acceptedPrivacyAt) { this.acceptedPrivacyAt = acceptedPrivacyAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}