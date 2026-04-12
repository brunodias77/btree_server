package com.btree.infrastructure.user.entity;


import com.btree.domain.user.entity.User;
import com.btree.domain.user.identifier.UserId;
import com.btree.infrastructure.persistence.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;


import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * FIX 5: @JoinTable agora declara explicitamente schema = "users".
 *
 * <p>O banco de dados usa schemas explícitos (users.user_roles). Sem o atributo
 * {@code schema}, o Hibernate resolve o nome da tabela usando o search_path
 * configurado na conexão JDBC. Isso funciona enquanto o search_path inclui
 * "users", mas falha silenciosamente em ambientes onde o search_path é apenas
 * "public" (padrão do PostgreSQL) — típico em Testcontainers e pipelines CI.
 *
 * <p>Declarar {@code schema = "users"} torna o mapeamento determinístico e
 * independente da configuração do servidor.
 */
@Entity
@Table(name = "users", schema = "users")
public class UserJpaEntity {

    @Id
    private UUID id;

    @Column(name = "username", nullable = false, length = 256, unique = true)
    private String username;

    @Column(name = "email", nullable = false, length = 256, unique = true)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "phone_number_verified", nullable = false)
    private boolean phoneNumberVerified;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    @Column(name = "access_failed_count", nullable = false)
    private int accessFailedCount;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private int version;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {
            CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH
    })
    @JoinTable(
            schema = "users",
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
    )
    private Set<RoleJpaEntity> roles = new HashSet<>();

    @jakarta.persistence.OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private ProfileJpaEntity profile;

    @jakarta.persistence.OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private NotificationPreferenceJpaEntity notificationPreference;

    public UserJpaEntity() {
    }

    private UserJpaEntity(
            final UUID id, final String username, final String email, final boolean emailVerified,
            final String passwordHash, final String phoneNumber, final boolean phoneNumberVerified,
            final boolean twoFactorEnabled, final String twoFactorSecret, final boolean accountLocked, final Instant lockExpiresAt,
            final int accessFailedCount, final boolean enabled, final Instant createdAt,
            final Instant updatedAt
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.emailVerified = emailVerified;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.phoneNumberVerified = phoneNumberVerified;
        this.twoFactorEnabled = twoFactorEnabled;
        this.twoFactorSecret = twoFactorSecret;
        this.accountLocked = accountLocked;
        this.lockExpiresAt = lockExpiresAt;
        this.accessFailedCount = accessFailedCount;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserJpaEntity from(final User aggregate) {
        var userEntity = new UserJpaEntity(
                aggregate.getId().getValue(),
                aggregate.getUsername(),
                aggregate.getEmail(),
                aggregate.isEmailVerified(),
                aggregate.getPasswordHash(),
                aggregate.getPhoneNumber(),
                aggregate.isPhoneNumberVerified(),
                aggregate.isTwoFactorEnabled(),
                aggregate.getTwoFactorSecret(),
                aggregate.isAccountLocked(),
                aggregate.getLockExpiresAt(),
                aggregate.getAccessFailedCount(),
                aggregate.isEnabled(),
                aggregate.getCreatedAt(),
                aggregate.getUpdatedAt()
        );


        userEntity.setProfile(ProfileJpaEntity.from(aggregate.getProfile(), userEntity));
        userEntity.setNotificationPreference(NotificationPreferenceJpaEntity.from(aggregate.getNotificationPreference(), userEntity));

        return userEntity;
    }

    public User toAggregate() {
        final var user = User.with(
                UserId.from(this.id),
                this.username,
                this.email,
                this.emailVerified,
                this.passwordHash,
                this.phoneNumber,
                this.phoneNumberVerified,
                this.twoFactorEnabled,
                this.twoFactorSecret,
                this.accountLocked,
                this.lockExpiresAt,
                this.accessFailedCount,
                this.enabled,
                this.createdAt,
                this.updatedAt,
                this.version,
                this.profile != null ? this.profile.toAggregate() : null,
                this.notificationPreference != null ? this.notificationPreference.toAggregate() : null
        );

        if (this.roles != null) {
            this.roles.forEach(role -> user.addRole(role.getName()));
        }

        return user;
    }

    /**
     * Atualiza campos mutáveis de um {@link UserJpaEntity} já gerenciado pelo Hibernate,
     * preservando o {@code version} rastreado pelo optimistic locking.
     * Usar em {@code update()} no gateway — nunca em {@code save()} (insert).
     */
    public void updateFrom(final User aggregate) {
        this.username = aggregate.getUsername();
        this.email = aggregate.getEmail();
        this.emailVerified = aggregate.isEmailVerified();
        this.passwordHash = aggregate.getPasswordHash();
        this.phoneNumber = aggregate.getPhoneNumber();
        this.phoneNumberVerified = aggregate.isPhoneNumberVerified();
        this.twoFactorEnabled = aggregate.isTwoFactorEnabled();
        this.twoFactorSecret = aggregate.getTwoFactorSecret();
        this.accountLocked = aggregate.isAccountLocked();
        this.lockExpiresAt = aggregate.getLockExpiresAt();
        this.accessFailedCount = aggregate.getAccessFailedCount();
        this.enabled = aggregate.isEnabled();
        this.updatedAt = aggregate.getUpdatedAt();

        if (aggregate.getProfile() != null && this.profile != null) {
            this.profile.updateFrom(aggregate.getProfile());
        }
        if (aggregate.getNotificationPreference() != null && this.notificationPreference != null) {
            this.notificationPreference.updateFrom(aggregate.getNotificationPreference());
        }
    }

    public Set<RoleJpaEntity> getRoles() { return roles; }
    public void setRoles(Set<RoleJpaEntity> roles) { this.roles = roles; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public boolean isPhoneNumberVerified() { return phoneNumberVerified; }
    public void setPhoneNumberVerified(boolean phoneNumberVerified) { this.phoneNumberVerified = phoneNumberVerified; }

    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }

    public boolean isAccountLocked() { return accountLocked; }
    public void setAccountLocked(boolean accountLocked) { this.accountLocked = accountLocked; }

    public Instant getLockExpiresAt() { return lockExpiresAt; }
    public void setLockExpiresAt(Instant lockExpiresAt) { this.lockExpiresAt = lockExpiresAt; }

    public int getAccessFailedCount() { return accessFailedCount; }
    public void setAccessFailedCount(int accessFailedCount) { this.accessFailedCount = accessFailedCount; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public ProfileJpaEntity getProfile() { return profile; }
    public void setProfile(ProfileJpaEntity profile) { this.profile = profile; }

    public NotificationPreferenceJpaEntity getNotificationPreference() { return notificationPreference; }
    public void setNotificationPreference(NotificationPreferenceJpaEntity notificationPreference) { this.notificationPreference = notificationPreference; }
}
