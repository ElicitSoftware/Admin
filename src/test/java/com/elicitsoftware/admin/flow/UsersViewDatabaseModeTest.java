package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.test.DatabaseAuthorizationTestProfile;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link UsersView}'s introduction in {@code DATABASE} authorization mode.
 *
 * <p>Traceability: UC-008 BR-183, UC-016 (Admin #192).</p>
 */
@QuarkusTest
@TestProfile(DatabaseAuthorizationTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
class UsersViewDatabaseModeTest extends QuarkusBrowserlessTest {

    /**
     * UC-008 BR-183: in DATABASE mode the introduction says roles are assigned on the Edit User
     * screen, that a role from the identity provider takes precedence, and names every role. It
     * never tells the administrator to configure roles in OIDC.
     */
    @Test
    void infoTextExplainsDatabaseModeAndTheProviderPrecedence() {
        UsersView view = CDI.current().select(UsersView.class).get();
        UI.getCurrent().add(view);
        String text = find(Paragraph.class, view).single().getText();
        assertTrue(text.contains("Edit User"), "DATABASE mode should point to the Edit User screen: " + text);
        assertTrue(text.contains("takes precedence"), "a role from the identity provider wins: " + text);
        for (String role : List.of("elicit_admin", "elicit_user", "elicit_importer")) {
            assertTrue(text.contains(role), "the text should name " + role + ": " + text);
        }
        assertFalse(text.contains("must be configured"), "DATABASE mode must not send roles to OIDC: " + text);
    }
}
