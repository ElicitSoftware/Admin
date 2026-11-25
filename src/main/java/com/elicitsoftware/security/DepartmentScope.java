package com.elicitsoftware.security;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.ImportClient;
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
 * <p>The rule reads from two places. A person registers where their console user record
 * (UC-008) is assigned, a machine client of the registration API where its import client
 * record (UC-032) is assigned, and a principal with neither registers nowhere.</p>
 *
 * <p>The record is read from the database on every call, in a persistence context
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
     * Whether the current principal may act in a department.
     *
     * <p>A principal whose name is that of an import client is that import client and nothing
     * else (UC-001 BR-171): its console user record, should one exist, is not consulted.</p>
     *
     * @param departmentId the department a record names
     * @return true when the principal's record is active and assigned to the department
     */
    @Transactional
    public boolean permits(long departmentId) {
        ImportClient client = importClient();
        if (client != null) {
            return actsAsImportClient(client) && client.getDepartments() != null
                    && client.getDepartments().stream().anyMatch(department -> department.id == departmentId);
        }
        User user = consoleRecord();
        // No record, no department: a role says the principal may call, the record says for
        // which departments.
        if (user == null || !user.isActive() || user.getDepartments() == null) {
            return false;
        }
        return user.getDepartments().stream().anyMatch(department -> department.id == departmentId);
    }

    /**
     * Refuses a principal that may not act in a department.
     *
     * @param departmentId the department a record names
     * @throws ForbiddenException when {@link #permits(long)} is false
     */
    @Transactional
    public void require(long departmentId) {
        if (!permits(departmentId)) {
            throw new ForbiddenException();
        }
    }

    /**
     * Refuses a caller of the registration API that is not an import client (UC-010 BR-170).
     * The refusal is of the whole request, before any record of it is read.
     *
     * @throws ForbiddenException when the principal has no active import client record, or its
     *         identity lacks {@code elicit_importer} or carries a console role beside it
     */
    @Transactional
    public void requireImportClient() {
        ImportClient client = importClient();
        if (client == null || !actsAsImportClient(client)) {
            throw new ForbiddenException();
        }
    }

    /**
     * An import client is its active record and the one role (UC-010 BR-170): a token that also
     * carries a console role is refused rather than read as either.
     */
    private boolean actsAsImportClient(ImportClient client) {
        return client.isActive()
                && identity.hasRole(ElicitRoles.IMPORTER)
                && !identity.hasRole(ElicitRoles.USER)
                && !identity.hasRole(ElicitRoles.ADMIN);
    }

    /** The import client record of the current principal, active or not; null when there is none. */
    ImportClient importClient() {
        return ImportClient.findByName(identity.getPrincipal().getName());
    }

    /** The console user record of the current principal, active or not; null when there is none. */
    User consoleRecord() {
        String principalName = identity.getPrincipal().getName();
        return User.find("username = ?1", principalName).firstResult(); // i18n:ignore (JPQL)
    }
}
