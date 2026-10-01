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
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted persistence tests for {@link DepartmentService}.
 *
 * <p>Traceability: UC-006 (Manage Departments). Code-review finding #7 moved the transactional
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
        department.name = "UC006 Dept " + suffix;
        department.code = "UC006-" + suffix;
        department.defaultMessageId = "1";
        department.fromEmail = "uc006@example.org";
        return department;
    }

    /** UC-006: saving a department with id 0 inserts a new row with a generated id. */
    @Test
    @TestTransaction
    void savePersistsNewDepartment() {
        Department saved = departmentService.save(newDepartment("new"));

        assertTrue(saved.id > 0, "a new department should receive a generated id");
        Department reloaded = Department.findById(saved.id);
        assertNotNull(reloaded, "the new department should be persisted and findable");
        assertEquals("UC006 Dept new", reloaded.name);
    }

    /** UC-006: saving a department with a non-zero id merges changes into the existing row. */
    @Test
    @TestTransaction
    void saveMergesExistingDepartment() {
        Department saved = departmentService.save(newDepartment("edit"));
        long id = saved.id;

        saved.name = "UC006 Dept edited";
        departmentService.save(saved);

        Department reloaded = Department.findById(id);
        assertNotNull(reloaded);
        assertEquals("UC006 Dept edited", reloaded.name);
        assertEquals(1, Department.count("code = ?1", "UC006-edit"),
                "update must not create a duplicate row");
    }
    private static User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setFirstName("Cee");
        user.setLastName("Creator");
        user.setActive(true);
        user.persist();
        return user;
    }

    /** UC-028 BR-113: creating a department assigns it to the creating administrator, in one transaction. */
    @Test
    @TestTransaction
    void createAssignsTheNewDepartmentToItsCreator() {
        persistUser("uc028.creator");

        Department created = departmentService.create(newDepartment("create"), "uc028.creator");
        assertTrue(created.id > 0, "the department should be persisted");

        // Read the join table back rather than the set the service mutated.
        User.getEntityManager().flush();
        User.getEntityManager().clear();
        User reloaded = User.find("username = ?1", "uc028.creator").firstResult();
        assertTrue(reloaded.getDepartments().stream().anyMatch(d -> d.id == created.id),
                "the creator should be assigned to the department they created");
    }

    /** UC-028: an unknown principal still gets the department; only the assignment is skipped. */
    @Test
    @TestTransaction
    void createWithAnUnknownPrincipalStillPersistsTheDepartment() {
        Department created = departmentService.create(newDepartment("orphan"), "uc028.nobody");

        assertNotNull(Department.findById(created.id), "the department is persisted regardless");
    }

    /** UC-028 BR-113 is about creation only: updating a department assigns nobody. */
    @Test
    @TestTransaction
    void saveDoesNotAssignOnUpdate() {
        persistUser("uc028.editor");
        Department saved = departmentService.save(newDepartment("update"));

        saved.name = "UC006 Dept renamed";
        departmentService.save(saved);

        User.getEntityManager().flush();
        User.getEntityManager().clear();
        User reloaded = User.find("username = ?1", "uc028.editor").firstResult();
        assertFalse(reloaded.hasDepartments(), "save() must not assign departments to anyone");
    }
}
