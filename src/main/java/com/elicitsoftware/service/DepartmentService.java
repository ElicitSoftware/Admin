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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

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
}
