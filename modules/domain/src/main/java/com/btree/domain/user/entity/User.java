package com.btree.domain.user.entity;

import com.btree.domain.user.event.UserCreatedEvent;
import com.btree.domain.user.identifier.UserId;
import com.btree.domain.user.validator.UserValidator;
import com.btree.shared.domain.AggregateRoot;
import com.btree.shared.domain.DomainException;
import com.btree.shared.validation.Notification;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A Raiz de Agregação (Aggregate Root) do subdomínio de Usuários.
 *
 * <p>Esta classe representa a fronteira de transação e consistência para a entidade User.
 * Alterações de estado em propriedades do Usuário, seu Perfil ou Preferências de Notificação
 * devem sempre passar por comportamentos expostos aqui.
 */
public class User extends AggregateRoot<UserId> {

    // ── State (Properties) ───────────────────────────────────


    private String username;
    private String email;
    private boolean emailVerified;
    private String passwordHash;
    private String phoneNumber;
    private boolean phoneNumberVerified;
    private boolean twoFactorEnabled;
    private String twoFactorSecret;
    private boolean accountLocked;
    private Instant lockExpiresAt;
    private int accessFailedCount;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
    private Profile profile;
    private NotificationPreference notificationPreference;
    private final Set<String> roles = new HashSet<>();

    // ── Constructors ─────────────────────────────────────────

    private User(
            final UserId id,
            final String username,
            final String email,
            final boolean emailVerified,
            final String passwordHash,
            final String phoneNumber,
            final boolean phoneNumberVerified,
            final boolean twoFactorEnabled,
            final String twoFactorSecret,
            final boolean accountLocked,
            final Instant lockExpiresAt,
            final int accessFailedCount,
            final boolean enabled,
            final Instant createdAt,
            final Instant updatedAt,
            final int version
    ) {
        super(id, version);
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

    // ── Factories ────────────────────────────────────────────

    /**
     * Factory Principal: Usada para criar um NOVO usuário do zero no sistema.
     * Além de instanciar a classe e suas composições filhas (Profile e NotificationPreference),
     * este método aciona a validação estrita e engatilha o Evento de Domínio de criação.
     */
    public static User create(final String username, final String email, final String passwordHash, final Notification notification){
        final var now = Instant.now();
        final var user = new User(
                UserId.unique(),
                username,
                email,
                false,
                passwordHash,
                null,
                false,
                false,
                null,
                false,
                null,
                0,
                true,
                now,
                now,
                0
        );

        user.profile = Profile.create(user.getId());
        user.notificationPreference = NotificationPreference.create(user.getId());
        user.validate(notification);
        if(notification.hasError()){
            throw DomainException.with(notification.getErrors());
        }

        user.registerEvent(new UserCreatedEvent(user.getId().getValue().toString(), user.getUsername(), user.getEmail()));
        return user;
    }

    /**
     * Factory de Reconstituição: Usada pelos Repositórios / Gateways (Infraestrutura)
     * para reconstruir em memória um User que já existe no Banco de Dados.
     * Não gera eventos e não aciona o validador.
     */
    public static User with(
            final UserId id,
            final String username,
            final String email,
            final boolean emailVerified,
            final String passwordHash,
            final String phoneNumber,
            final boolean phoneNumberVerified,
            final boolean twoFactorEnabled,
            final String twoFactorSecret,
            final boolean accountLocked,
            final Instant lockExpiresAt,
            final int accessFailedCount,
            final boolean enabled,
            final Instant createdAt,
            final Instant updatedAt,
            final int version,
            final Profile profile,
            final NotificationPreference notificationPreference
    ) {
        final var user = new User(
                id, username, email, emailVerified, passwordHash,
                phoneNumber, phoneNumberVerified, twoFactorEnabled,
                twoFactorSecret, accountLocked, lockExpiresAt, accessFailedCount,
                enabled, createdAt, updatedAt, version
        );
        user.profile = profile;
        user.notificationPreference = notificationPreference;
        return user;
    }

    // ── Domain Behaviors ─────────────────────────────────────

    @Override
    public void validate(ValidationHandler handler) {
        new UserValidator(this, handler).validate();
    }

    public void addRole(String role){
        if(role != null && !role.isBlank()){
            this.roles.add(role);
        }
    }

    // ── Getters (Read-Only Exposure) ─────────────────────────

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public boolean isPhoneNumberVerified() {
        return phoneNumberVerified;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }

    public int getAccessFailedCount() {
        return accessFailedCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Profile getProfile() {
        return profile;
    }

    public NotificationPreference getNotificationPreference() {
        return notificationPreference;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
