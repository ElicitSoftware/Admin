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
     * @return true when the principal has a console record that is active and assigned to the
     *         department; false for a principal with no console record, whatever role the
     *         identity provider gave it (UC-010 BR-137)
     */
    @Transactional
    public boolean permits(long departmentId) {
        User user = consoleRecord();
        // No console record, no department: a role says the principal may call, the record says
        // for which departments. An integration client is entered on the Users screen like a person.
        if (user == null || !user.isActive() || user.getDepartments() == null) {
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

    /** The console record of the current principal, active or not; null when there is none. */
    User consoleRecord() {
        String principalName = identity.getPrincipal().getName();
        return User.find("username = ?1", principalName).firstResult(); // i18n:ignore (JPQL)
    }
}
