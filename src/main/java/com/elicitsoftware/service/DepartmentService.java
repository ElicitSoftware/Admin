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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.HashSet;

/**
 * Application-scoped service that owns persistence for {@link Department} entities.
 *
 * <p>Keeping the transactional data-access logic here (rather than in the Vaadin view) lets the
 * UI layer stay focused on presentation and validation, and gives a single, testable seam for
 * department create/update behaviour.</p>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Department
 */
@ApplicationScoped
public class DepartmentService {

    /**
     * Default constructor for CDI.
     */
    public DepartmentService() {
        // Default constructor for CDI
    }

    /**
     * Persists a new department or merges changes into an existing one, in a single transaction.
     *
     * <p>A department whose id is {@code 0} is treated as new and inserted; any other id is
     * treated as an update and merged.</p>
     *
     * @param department the department to save; must not be {@code null}
     * @return the managed, saved department instance
     */
    @Transactional
    public Department save(Department department) {
        if (department.id == 0) {
            department.persist();
            return department;
        }
        return Department.getEntityManager().merge(department);
    }

    /**
     * Inserts a new department and assigns it to the administrator creating it, in one
     * transaction (UC-028 BR-113).
     *
     * <p>A fresh deployment has no department, and the administrator who creates the first
     * one must be able to use it at once. Persisting the department and adding it to the
     * creator's departments in the same transaction means an administrator can never end up
     * having created a department they are not assigned to. The rule is unconditional: an
     * administrator who sets up several departments belongs to all of them.</p>
     *
     * <p>An unknown or inactive principal still gets the department persisted; only the
     * assignment is skipped, and the caller's session record is then simply unchanged.</p>
     *
     * @param department    the new department; must have id {@code 0}
     * @param principalName the username of the signed-in administrator
     * @return the managed, persisted department
     */
    @Transactional
    public Department create(Department department, String principalName) {
        department.persist();
        User owner = User.find("username = ?1 and active = true", principalName).firstResult(); // i18n:ignore (JPQL)
        if (owner != null) {
            // The join table is owned by User, so mutating the managed set writes the row on flush.
            // A record persisted in this same persistence context has no set yet.
            if (owner.getDepartments() == null) {
                owner.setDepartments(new HashSet<>());
            }
            owner.getDepartments().add(department);
        }
        return department;
    }
}
