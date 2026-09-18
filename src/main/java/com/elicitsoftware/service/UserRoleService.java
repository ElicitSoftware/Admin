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
 * <p>Two independent grants are kept per user (UC-016 BR-055): at most one raw <em>ladder</em>
 * grant -- {@link #setLadderRole} deletes any existing ladder row before inserting the selected
 * one, since the admin/user/importer hierarchy ({@link ElicitRoles#expand}) means only the
 * highest role ever needs to be stored -- and an optional {@code elicit_analytics} row
 * ({@link #setAnalytics}, UC-020) that the ladder methods never touch.</p>
 */
@ApplicationScoped
public class UserRoleService {

    /** Creates the service; entity access goes through {@link UserRole}'s Panache methods. */
    public UserRoleService() {
    }

    /**
     * Returns the user's current raw ladder grant ({@code elicit_admin}, {@code elicit_user}
     * or {@code elicit_importer}), if any. The analytics grant is reported separately by
     * {@link #hasAnalytics(long)}.
     *
     * @param userId the user's id
     * @return the raw ladder role name, or empty if the user has no ladder grant
     */
    public Optional<String> findLadderRole(long userId) {
        List<UserRole> roles = UserRole.list("id.userId", userId);
        return roles.stream()
                .map(r -> r.getId().getRoleName())
                .filter(ElicitRoles.LADDER::contains)
                .findFirst();
    }

    /**
     * Sets the user's ladder grant to exactly {@code roleName}, replacing any existing ladder
     * grant and leaving an analytics grant untouched (UC-016 BR-055). A no-op when the user
     * already holds {@code roleName}, so repeated saves within one persistence context never
     * re-persist an entity that is still managed.
     *
     * @param userId the user's id
     * @param roleName the raw ladder role to grant
     * @throws IllegalArgumentException if {@code roleName} is not a ladder role
     */
    @Transactional
    public void setLadderRole(long userId, String roleName) {
        if (!ElicitRoles.LADDER.contains(roleName)) {
            throw new IllegalArgumentException("Unrecognized ladder role: " + roleName);
        }
        if (findLadderRole(userId).filter(roleName::equals).isPresent()) {
            return;
        }
        deleteLadderRows(userId);
        new UserRole(userId, roleName).persist();
    }

    /**
     * Removes the user's ladder grant, leaving an analytics grant untouched.
     *
     * @param userId the user's id
     */
    @Transactional
    public void clearLadderRole(long userId) {
        deleteLadderRows(userId);
    }

    /**
     * Reports whether the user holds the {@code elicit_analytics} grant (UC-020).
     *
     * @param userId the user's id
     * @return {@code true} if an analytics row exists for the user
     */
    public boolean hasAnalytics(long userId) {
        return UserRole.count("id.userId = ?1 and id.roleName = ?2", userId, ElicitRoles.ANALYTICS) > 0;
    }

    /**
     * Adds or removes the user's {@code elicit_analytics} grant without touching the ladder
     * grant (UC-016 BR-055).
     *
     * @param userId the user's id
     * @param granted {@code true} to grant analytics, {@code false} to revoke it
     */
    @Transactional
    public void setAnalytics(long userId, boolean granted) {
        boolean has = hasAnalytics(userId);
        if (granted && !has) {
            new UserRole(userId, ElicitRoles.ANALYTICS).persist();
        } else if (!granted && has) {
            UserRole.delete("id.userId = ?1 and id.roleName = ?2", userId, ElicitRoles.ANALYTICS);
        }
    }

    private static void deleteLadderRows(long userId) {
        UserRole.delete("id.userId = ?1 and id.roleName in ?2", userId, ElicitRoles.LADDER);
    }
}
