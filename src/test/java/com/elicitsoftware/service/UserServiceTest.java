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

import com.elicitsoftware.model.User;
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
 * Booted persistence tests for {@link UserService}.
 *
 * <p>Traceability: UC-009 (Manage Users). Code-review finding #7 moved the transactional
 * persistence out of {@code EditUserView} into this service; these tests exercise that seam
 * directly — insert-on-create ({@code id == 0}) and merge-on-update ({@code id != 0}) — against
 * the real schema on the shared PostgreSQL container.</p>
 *
 * <p>Each test runs in a rolled-back transaction ({@link TestTransaction}) so the container
 * stays clean between tests.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UserServiceTest {

    @Inject
    UserService userService;

    /** UC-009: saving a user with id 0 inserts a new row with a generated id. */
    @Test
    @TestTransaction
    void savePersistsNewUser() {
        User user = new User();
        user.setUsername("uc009.new@example.org");
        user.setFirstName("New");
        user.setLastName("User");
        user.setActive(true);

        User saved = userService.save(user);

        assertTrue(saved.getId() > 0, "a new user should receive a generated id");
        User reloaded = User.findById(saved.getId());
        assertNotNull(reloaded, "the new user should be persisted and findable");
        assertEquals("uc009.new@example.org", reloaded.getUsername());
        assertTrue(reloaded.isActive());
    }

    /** UC-009: saving a user with a non-zero id merges changes into the existing row. */
    @Test
    @TestTransaction
    void saveMergesExistingUser() {
        User user = new User();
        user.setUsername("uc009.edit@example.org");
        user.setFirstName("Before");
        user.setLastName("Edit");
        user.setActive(true);
        User saved = userService.save(user);
        long id = saved.getId();

        // Mutate and save again — should update in place, not insert a second row.
        saved.setFirstName("After");
        saved.setActive(false);
        userService.save(saved);

        User reloaded = User.findById(id);
        assertNotNull(reloaded);
        assertEquals("After", reloaded.getFirstName());
        assertEquals(false, reloaded.isActive());
        assertEquals(1, User.count("username = ?1", "uc009.edit@example.org"),
                "update must not create a duplicate row");
    }
}
