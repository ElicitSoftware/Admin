package com.elicitsoftware.model;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * A single role grant in {@code survey.user_roles}: composite key of (userId, roleName).
 *
 * <p>Used by the admin UI ({@code UserRoleService}) to manage direct database role grants
 * when {@code elicit.authorization.mode=DATABASE}. Not used by
 * {@code com.elicitsoftware.security.UserRoleDatabaseLookup}, which reads this table via a
 * native query for security-augmentation performance reasons.</p>
 */
@Entity
@Table(name = "user_roles", schema = "survey")
public class UserRole extends PanacheEntityBase {

    @EmbeddedId
    private UserRoleId id;

    /** Creates an empty instance for JPA to populate. */
    public UserRole() {
    }

    /** Creates a role grant for the given user and role name. */
    public UserRole(long userId, String roleName) {
        this.id = new UserRoleId(userId, roleName);
    }

    /** Returns the composite key. */
    public UserRoleId getId() {
        return id;
    }

    /** Sets the composite key. */
    public void setId(UserRoleId id) {
        this.id = id;
    }

    /**
     * Compares this role grant to another based on the composite key ({@link UserRoleId}
     * already implements value equality).
     *
     * @param o the object to compare with
     * @return {@code true} if the other object is a {@code UserRole} with an equal composite key
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserRole other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    /**
     * Returns a hash code derived from the composite key, consistent with {@link #equals(Object)}.
     *
     * @return the hash code for this role grant
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** Composite key for {@link UserRole}: a (userId, roleName) pair. */
    @Embeddable
    public static class UserRoleId implements Serializable {

        @Column(name = "user_id")
        private long userId;

        @Column(name = "role_name")
        private String roleName;

        /** Creates an empty instance for JPA to populate. */
        public UserRoleId() {
        }

        /** Creates a key for the given user and role name. */
        public UserRoleId(long userId, String roleName) {
            this.userId = userId;
            this.roleName = roleName;
        }

        /** Returns the user's id. */
        public long getUserId() {
            return userId;
        }

        /** Sets the user's id. */
        public void setUserId(long userId) {
            this.userId = userId;
        }

        /** Returns the role name. */
        public String getRoleName() {
            return roleName;
        }

        /** Sets the role name. */
        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UserRoleId other)) {
                return false;
            }
            return userId == other.userId && Objects.equals(roleName, other.roleName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleName);
        }
    }
}
