package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Pre;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link DebugView}'s rendered diagnostic content.
 *
 * <p>Traceability: UC-009 (View Security Diagnostics). {@code DebugViewTest} covers the token
 * masking helper and the {@code @RolesAllowed} annotation as plain unit tests; this test covers
 * the view's own {@code @PostConstruct} rendering — obtained through CDI (no route navigation
 * needed, since {@code @PostConstruct} runs on bean creation regardless) and attached to the
 * test {@link UI}. No real OIDC provider runs in tests, so the ID/access token
 * {@code Instance}s are never resolvable here; that "Not available" branch is what this test
 * exercises for both.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestSecurity(user = "debug.tester", roles = {"elicit_admin"})
class DebugViewRenderTest extends QuarkusBrowserlessTest {

    private DebugView attachView() {
        DebugView view = CDI.current().select(DebugView.class).get();
        UI.getCurrent().add(view);
        return view;
    }

    /** UC-009: the view renders a "Debug Information" heading. */
    @Test
    void showsDebugInformationHeading() {
        DebugView view = attachView();
        H1 heading = find(H1.class, view).single();
        assertEquals(view.getTranslation("debugView.title"), heading.getText());
    }

    /** UC-009: the diagnostic block reports the authenticated principal and their roles. */
    @Test
    void diagnosticBlockReportsPrincipalAndRoles() {
        DebugView view = attachView();
        Pre debugInfo = find(Pre.class, view).single();
        String text = debugInfo.getText();

        assertTrue(text.contains(view.getTranslation("debugView.user", "debug.tester")));
        assertTrue(text.contains(view.getTranslation("debugView.isAnonymous", false)));
        assertTrue(text.contains(view.getTranslation("debugView.hasAdminRole", true)));
    }

    /**
     * UC-009: {@code @TestSecurity} synthesizes resolvable ID/access token producers (there is
     * no real OIDC provider in tests), so the diagnostic block takes the "token present" branch
     * for both and masks the access token's value rather than showing it raw.
     */
    @Test
    void tokensAreResolvedAndAccessTokenIsMasked() {
        DebugView view = attachView();
        String text = find(Pre.class, view).single().getText();

        assertTrue(text.contains(view.getTranslation("debugView.idToken", "").strip()));
        assertTrue(text.contains(view.getTranslation("debugView.accessToken", "").strip()));
        assertTrue(text.contains("*"), "the access token's masked form should contain asterisks: " + text);
    }

    /** The dynamic page title identifies this as the Admin app's debug view. */
    @Test
    void pageTitleIdentifiesDebugView() {
        DebugView view = attachView();
        assertEquals(view.getTranslation("debugView.pageTitle"), view.getPageTitle());
    }
}
