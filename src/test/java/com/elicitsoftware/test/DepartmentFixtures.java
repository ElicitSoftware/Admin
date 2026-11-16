package com.elicitsoftware.test;


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
import io.quarkus.narayana.jta.QuarkusTransaction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Committed departments and console user records for the tests of the department rule (NFR-021).
 * The rule reads the console record in a transaction of its own, and the integration API is
 * called over HTTP, so the rows have to be committed rather than left in a test transaction.
 */
public final class DepartmentFixtures {

    private DepartmentFixtures() {
    }

    /** Persists a department under a name and code made unique by the token. */
    public static Department department(String token) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Department department = new Department();
            department.name = "Scope Dept " + token;
            department.code = "SC-" + token;
            // "1" need not resolve to a real MessageTemplate row: Message.createMessagesForSubject
            // just logs and skips an unresolvable template id rather than failing the registration.
            department.defaultMessageId = "1";
            department.fromEmail = "scope@example.org";
            department.persist();
            return department;
        });
    }

    /** Creates the console record for a username, or resets it, with exactly these departments. */
    public static User consoleRecord(String username, boolean active, Department... departments) {
        return QuarkusTransaction.requiringNew().call(() -> {
            User user = User.find("username = ?1", username).firstResult();
            if (user == null) {
                user = new User();
                user.setUsername(username);
                user.setFirstName("Scope");
                user.setLastName("Tester");
            }
            user.setActive(active);
            Set<Department> assigned = new HashSet<>();
            for (Department department : departments) {
                assigned.add(Department.findById(department.id));
            }
            user.setDepartments(assigned);
            user.persist();
            return user;
        });
    }

    /** Removes the console record of a username, if there is one. */
    public static void noConsoleRecord(String username) {
        QuarkusTransaction.requiringNew().run(() -> {
            List<User> users = User.list("username = ?1", username);
            users.forEach(User::delete);
        });
    }
}
