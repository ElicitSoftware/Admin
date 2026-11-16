package com.elicitsoftware.security;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;

/**
 * The departments the current principal may register, open or change a subject in (NFR-021;
 * UC-010 BR-137, UC-003 BR-138). This is the one place the rule is decided: the integration API,
 * the CSV upload and the registration form all ask here.
 *
 * <p>The console user record is read from the database on every call, in a persistence context
 * of its own when the caller has none, so a department assigned or removed since sign-in counts
 * at once; the copy a browser session holds only feeds what a screen offers.</p>
 */
@ApplicationScoped
public class DepartmentScope {

    @Inject
    SecurityIdentity identity;

    /** Creates the bean; the identity is populated by CDI injection. */
    public DepartmentScope() {
    }

    /**
     * Whether the current principal may act on subjects of a department.
     *
     * @param departmentId the department a subject belongs to, or is to be registered in
     * @return true when the principal's console record is active and assigned to the department,
     *         or when the principal has no console record ({@link #permitsWithoutConsoleRecord()})
     */
    @Transactional
    public boolean permits(long departmentId) {
        User user = consoleRecord();
        if (user == null) {
            return permitsWithoutConsoleRecord();
        }
        if (!user.isActive() || user.getDepartments() == null) {
            return false;
        }
        return user.getDepartments().stream().anyMatch(department -> department.id == departmentId);
    }

    /**
     * Refuses the request when {@link #permits(long)} does not hold.
     *
     * @param departmentId the department a subject belongs to, or is to be registered in
     * @throws ForbiddenException when the principal may not act on that department
     */
    @Transactional
    public void require(long departmentId) {
        if (!permits(departmentId)) {
            throw new ForbiddenException();
        }
    }

    /**
     * What a principal with no console user record may do. Such a principal — an integration
     * client that holds a role from the identity provider but was never entered on the Users
     * screen — is not bound to departments (UC-010 BR-137). This method is the single place that
     * decides it: returning {@code false} here makes a console record a requirement for every
     * caller.
     *
     * @return whether a principal without a console record is permitted
     */
    boolean permitsWithoutConsoleRecord() {
        return true;
    }

    /** The console record of the current principal, active or not; null when there is none. */
    User consoleRecord() {
        String principalName = identity.getPrincipal().getName();
        return User.find("username = ?1", principalName).firstResult(); // i18n:ignore (JPQL)
    }
}
