package com.elicitsoftware.service;

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

import com.elicitsoftware.model.UserRole;
import com.elicitsoftware.security.ElicitRoles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application-scoped service owning persistence for direct {@code survey.user_roles}
 * grants managed through the admin UI (available only when
 * {@code elicit.authorization.mode=DATABASE}).
 *
 * <p>A user holds at most one raw grant at a time -- {@link #setRole} deletes any existing
 * grant before inserting the selected one, rather than accumulating multiple rows, since the
 * admin/user/importer hierarchy ({@link ElicitRoles#expand}) means only the highest role ever
 * needs to be stored.</p>
 */
@ApplicationScoped
public class UserRoleService {

    /** Creates the service; entity access goes through {@link UserRole}'s Panache methods. */
    public UserRoleService() {
    }

    /**
     * Returns the user's current raw role grant, if any.
     *
     * @param userId the user's id
     * @return the raw role name, or empty if the user has no grant
     */
    public Optional<String> findRoleName(long userId) {
        List<UserRole> roles = UserRole.list("id.userId", userId);
        return roles.isEmpty() ? Optional.empty() : Optional.of(roles.get(0).getId().getRoleName());
    }

    /**
     * Sets the user's role grant to exactly {@code roleName}, replacing any existing grant.
     *
     * @param userId the user's id
     * @param roleName the raw role to grant
     * @throws IllegalArgumentException if {@code roleName} is not a recognized Elicit role
     */
    @Transactional
    public void setRole(long userId, String roleName) {
        if (!ElicitRoles.ALL.contains(roleName)) {
            throw new IllegalArgumentException("Unrecognized role: " + roleName);
        }
        UserRole.delete("id.userId", userId);
        new UserRole(userId, roleName).persist();
    }

    /**
     * Removes the user's role grant entirely.
     *
     * @param userId the user's id
     */
    @Transactional
    public void clearRole(long userId) {
        UserRole.delete("id.userId", userId);
    }
}
