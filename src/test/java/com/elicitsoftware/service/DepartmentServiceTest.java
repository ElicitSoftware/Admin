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
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted persistence tests for {@link DepartmentService}.
 *
 * <p>Traceability: UC-010 (Manage Departments). Code-review finding #7 moved the transactional
 * persistence out of {@code EditDepartmentView} into this service; these tests exercise that
 * seam directly — insert-on-create ({@code id == 0}) and merge-on-update ({@code id != 0}) —
 * against the real schema on the shared PostgreSQL container.</p>
 *
 * <p>Each test runs in a rolled-back transaction ({@link TestTransaction}) so the container
 * stays clean between tests.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DepartmentServiceTest {

    @Inject
    DepartmentService departmentService;

    private Department newDepartment(String suffix) {
        Department department = new Department();
        department.name = "UC010 Dept " + suffix;
        department.code = "UC010-" + suffix;
        department.defaultMessageId = "1";
        department.fromEmail = "uc010@example.org";
        return department;
    }

    /** UC-010: saving a department with id 0 inserts a new row with a generated id. */
    @Test
    @TestTransaction
    void savePersistsNewDepartment() {
        Department saved = departmentService.save(newDepartment("new"));

        assertTrue(saved.id > 0, "a new department should receive a generated id");
        Department reloaded = Department.findById(saved.id);
        assertNotNull(reloaded, "the new department should be persisted and findable");
        assertEquals("UC010 Dept new", reloaded.name);
    }

    /** UC-010: saving a department with a non-zero id merges changes into the existing row. */
    @Test
    @TestTransaction
    void saveMergesExistingDepartment() {
        Department saved = departmentService.save(newDepartment("edit"));
        long id = saved.id;

        saved.name = "UC010 Dept edited";
        departmentService.save(saved);

        Department reloaded = Department.findById(id);
        assertNotNull(reloaded);
        assertEquals("UC010 Dept edited", reloaded.name);
        assertEquals(1, Department.count("code = ?1", "UC010-edit"),
                "update must not create a duplicate row");
    }
}
