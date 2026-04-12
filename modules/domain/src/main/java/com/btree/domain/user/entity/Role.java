package com.btree.domain.user.entity;

import com.btree.domain.user.error.RoleError;
import com.btree.domain.user.identifier.RoleId;
import com.btree.shared.domain.Entity;
import com.btree.shared.validation.ValidationHandler;

import java.time.Instant;

/**
 * Entity — maps to {@code users.roles} table.
 */
public class Role extends Entity<RoleId> {

    private String name;
    private String description;
    private Instant createdAt;

    private Role(final RoleId id, final String name, final String description, final Instant createdAt) {
        super(id);
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static Role create(final String name, final String description) {
        final var role = new Role(RoleId.unique(), name, description, Instant.now());
        final var notification = new com.btree.shared.validation.Notification();
        role.validate(notification);
        if (notification.hasError()) {
            throw com.btree.shared.domain.DomainException.with(notification.getErrors());
        }
        return role;
    }

    public static Role with(final RoleId id, final String name, final String description, final Instant createdAt) {
        return new Role(id, name, description, createdAt);
    }

    @Override
    public void validate(final ValidationHandler handler) {
        if (name == null || name.isBlank()) {
            handler.append(RoleError.NAME_EMPTY);
        }
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }


}
