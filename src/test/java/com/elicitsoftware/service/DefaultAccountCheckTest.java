package com.elicitsoftware.service;

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
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seeded default accounts are recognised by username and the warning clears once they are
 * renamed.
 *
 * <p>Traceability: UC-021 (Warn About Default Accounts), BR-085, BR-086, BR-088.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DefaultAccountCheckTest {

    @Inject
    DefaultAccountCheck check;

    private static void ensureSeededAccount(String username) {
        QuarkusTransaction.requiringNew().run(() -> {
            if (User.find("username", username).firstResult() == null) {
                User user = new User();
                user.setUsername(username);
                user.setFirstName("Seeded");
                user.setLastName(username);
                user.setActive(true);
                user.persist();
            }
        });
    }

    private static void rename(String from, String to) {
        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.find("username", from).firstResult();
            if (user != null) {
                user.setUsername(to);
            }
        });
    }

    /** UC-021 step 1 / BR-085: the seeded usernames are found by name. */
    @Test
    void findsTheSeededAccountsByUsername() {
        ensureSeededAccount("admin");
        ensureSeededAccount("user");

        List<String> found = check.findDefaultAccounts();

        assertEquals(List.of("admin", "user"), found, "both seeded usernames should be reported, in seed order");
    }

    /** UC-021 step 7 / BR-086: once renamed, the check answers empty without a restart. */
    @Test
    void renamedAccountsAreNoLongerReported() {
        ensureSeededAccount("admin");
        ensureSeededAccount("user");
        try {
            rename("admin", "site.admin.renamed");
            rename("user", "site.user.renamed");

            assertTrue(check.findDefaultAccounts().isEmpty(), "renamed accounts must not be reported");
        } finally {
            rename("site.admin.renamed", "admin");
            rename("site.user.renamed", "user");
        }
    }

    /** UC-021 BR-088: the instruction names the accounts and covers the identity provider. */
    @Test
    void instructionNamesTheAccountsAndBothStores() {
        String text = DefaultAccountCheck.instruction(List.of("admin", "user"));

        assertTrue(text.contains("'admin' and 'user'"), text);
        assertTrue(text.contains("Users"), "the instruction should point at the Users screen");
        assertTrue(text.contains("identity provider"), "the instruction should cover DATABASE mode");
        assertEquals("", DefaultAccountCheck.instruction(List.of()));
    }
}
