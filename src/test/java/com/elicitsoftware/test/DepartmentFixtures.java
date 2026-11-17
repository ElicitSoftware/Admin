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
import com.elicitsoftware.model.ImportClient;
import com.elicitsoftware.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Committed departments, console user records and import client records for the tests of the department rule (NFR-021).
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
            // UC-006 BR-172: it registers onto the survey the test bootstrap seeds.
            offerInTransaction(department, SEEDED_SURVEY);
            return department;
        });
    }

    /** The survey the test bootstrap seeds (V0.0.0.1__TEST_BOOTSTRAP.sql). */
    public static final int SEEDED_SURVEY = 1;

    /**
     * Lets a department register onto a survey (UC-006 BR-172), in the caller's transaction, for
     * a test that persists its own department.
     */
    public static void offer(Department department, int surveyId) {
        offerInTransaction(department, surveyId);
    }

    private static void offerInTransaction(Department department, int surveyId) {
        Department.getEntityManager()
                .createNativeQuery("INSERT INTO survey.department_surveys (department_id, survey_id) VALUES (?1, ?2) "
                        + "ON CONFLICT DO NOTHING")
                .setParameter(1, department.id)
                .setParameter(2, surveyId)
                .executeUpdate();
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

    /**
     * Assigns a department to a username's console record inside the caller's transaction,
     * creating the record when there is none. For a {@code @TestTransaction} test that persists
     * its own department and then registers into it: the rule (UC-010 BR-137) joins that
     * transaction, and the rollback removes the record again.
     */
    public static User assign(String username, Department department) {
        User user = User.find("username = ?1", username).firstResult();
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setFirstName("Scope");
            user.setLastName("Tester");
            user.setDepartments(new HashSet<>());
        }
        user.setActive(true);
        if (user.getDepartments() == null) {
            user.setDepartments(new HashSet<>());
        }
        user.getDepartments().add(department);
        user.persistAndFlush();
        return user;
    }

    /** Removes the console record of a username, if there is one. */
    public static void noConsoleRecord(String username) {
        QuarkusTransaction.requiringNew().run(() -> {
            List<User> users = User.list("username = ?1", username);
            users.forEach(User::delete);
        });
    }

    /**
     * Creates the import client record for a principal name (UC-032), or resets it, with exactly
     * these departments.
     */
    public static ImportClient importClient(String name, boolean active, Department... departments) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ImportClient client = ImportClient.findByName(name);
            if (client == null) {
                client = new ImportClient();
                client.setName(name);
            }
            client.setActive(active);
            Set<Department> assigned = new HashSet<>();
            for (Department department : departments) {
                assigned.add(Department.findById(department.id));
            }
            client.setDepartments(assigned);
            client.persist();
            return client;
        });
    }

    /**
     * Assigns a department to a principal's import client record inside the caller's
     * transaction, creating the record when there is none: the import client's counterpart of
     * {@link #assign(String, Department)}.
     */
    public static ImportClient assignImportClient(String name, Department department) {
        ImportClient client = ImportClient.findByName(name);
        if (client == null) {
            client = new ImportClient();
            client.setName(name);
        }
        client.setActive(true);
        if (client.getDepartments() == null) {
            client.setDepartments(new HashSet<>());
        }
        client.getDepartments().add(department);
        client.persistAndFlush();
        return client;
    }

    /** Removes the import client record of a principal name, if there is one. */
    public static void noImportClient(String name) {
        QuarkusTransaction.requiringNew().run(() -> {
            List<ImportClient> clients = ImportClient.list("name = ?1", name);
            clients.forEach(ImportClient::delete);
        });
    }
}
