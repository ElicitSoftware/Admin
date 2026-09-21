package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.diagnostics.RequiredConfigCheck;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five System screens render for an administrator, show their sections, and never render a
 * secret.
 *
 * <p>Traceability: UC-020 (View System Overview), UC-022 (Diagnose Database), UC-023 (Diagnose
 * Branding), UC-024 (Diagnose Email), UC-025 (Check Connections); NFR-011.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestSecurity(user = "system.admin", roles = {"elicit_admin"})
class SystemViewsRenderTest extends QuarkusBrowserlessTest {

    /** The values the test profile configures; none may appear on a screen (NFR-011). */
    private static final String[] SECRETS = {"test-secret", "postgres"};

    @Inject
    MockMailbox mailbox;

    @Inject
    RequiredConfigCheck requiredConfig;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    private <T extends Component> T attach(Class<T> type) {
        T view = CDI.current().select(type).get();
        UI.getCurrent().add(view);
        return view;
    }

    private static boolean containsId(Component root, String id) {
        if (root.getId().filter(id::equals).isPresent()) {
            return true;
        }
        return root.getChildren().anyMatch(child -> containsId(child, id));
    }

    private void assertNoSecretRendered(Component view) {
        String text = view.getElement().getTextRecursively();
        for (String secret : SECRETS) {
            assertFalse(text.contains(secret), "a secret value must never be rendered: " + secret);
        }
    }

    /** UC-020: the overview shows build, health, required settings and outstanding setup. */
    @Test
    void overviewRendersEverySection() {
        SystemOverviewView view = attach(SystemOverviewView.class);

        assertEquals(view.getTranslation("systemOverviewView.title"), find(H3.class, view).single().getText());
        assertTrue(containsId(view, SystemOverviewView.BUILD_GRID_ID));
        assertTrue(containsId(view, SystemOverviewView.HEALTH_GRID_ID));
        assertTrue(containsId(view, SystemOverviewView.CONFIG_GRID_ID));
        assertTrue(containsId(view, SystemOverviewView.WARNINGS_ID) || containsId(view, SystemOverviewView.NO_WARNINGS_ID),
                "the overview must list outstanding setup or say there is none");
        assertNoSecretRendered(view);
    }

    /** UC-020 step 4 / BR-082: required settings are reported as present, never by value. */
    @Test
    void requiredSettingsAreReportedByPresenceOnly() {
        var settings = requiredConfig.requiredSettings();

        assertTrue(settings.stream().anyMatch(s -> s.property().equals("quarkus.datasource.password") && s.present()));
        assertTrue(settings.stream().anyMatch(s -> s.property().equals("quarkus.oidc.credentials.secret") && s.present()));
        assertTrue(settings.stream().anyMatch(s -> s.property().equals("quarkus.mailer.from") && s.present()));
        for (var setting : settings) {
            for (String secret : SECRETS) {
                assertFalse(setting.toString().contains(secret), "a required-setting report carries no value");
            }
        }
    }

    /** UC-022: the database screen shows connections, migrations and content. */
    @Test
    void databaseViewRendersConnectionsAndMigrations() {
        SystemDatabaseView view = attach(SystemDatabaseView.class);

        assertEquals(view.getTranslation("systemDatabaseView.title"), find(H3.class, view).single().getText());
        assertTrue(containsId(view, SystemDatabaseView.CONNECTIONS_GRID_ID));
        assertTrue(containsId(view, SystemDatabaseView.MIGRATIONS_GRID_ID));
        assertTrue(containsId(view, SystemDatabaseView.CONTENT_GRID_ID));
        assertNoSecretRendered(view);
    }

    /** UC-023: the branding screen shows resolution and assets, and reload re-renders. */
    @Test
    void brandingViewRendersAssetsAndReloads() {
        SystemBrandingView view = attach(SystemBrandingView.class);

        assertEquals(view.getTranslation("systemBrandingView.title"), find(H3.class, view).single().getText());
        assertTrue(containsId(view, SystemBrandingView.SUMMARY_GRID_ID));
        assertTrue(containsId(view, SystemBrandingView.ASSETS_GRID_ID));

        view.render();

        assertEquals(2, find(Grid.class, view).all().size(), "reload must replace the grids, not duplicate them");
    }

    /** UC-024: the email screen shows settings and sends through the mock mailer. */
    @Test
    void emailViewShowsSettingsAndSendsATest() {
        SystemEmailView view = attach(SystemEmailView.class);

        assertEquals(view.getTranslation("systemEmailView.title"), find(H3.class, view).single().getText());
        assertTrue(containsId(view, SystemEmailView.SETTINGS_GRID_ID));
        assertNoSecretRendered(view);

        view.recipient.setValue("ops@example.org");
        view.send();

        assertEquals(1, mailbox.getMailsSentTo("ops@example.org").size(), "one test message should be sent");
    }

    /** UC-024 A3: an empty recipient sends nothing. */
    @Test
    void emailViewRefusesAnEmptyRecipient() {
        SystemEmailView view = attach(SystemEmailView.class);

        view.recipient.setValue("");
        view.send();

        assertTrue(mailbox.getTotalMessagesSent() == 0, "nothing should be sent without a recipient");
    }

    /** UC-025: with nothing configured or stored, the connections screen says so. */
    @Test
    void connectionsViewRendersItsTargets() {
        SystemConnectionsView view = attach(SystemConnectionsView.class);

        assertEquals(view.getTranslation("systemConnectionsView.title"), find(H3.class, view).single().getText());
        if (view.rows().isEmpty()) {
            assertTrue(containsId(view, SystemConnectionsView.EMPTY_ID));
            assertTrue(find(Paragraph.class, view).all().size() >= 2);
        } else {
            assertTrue(containsId(view, SystemConnectionsView.GRID_ID));
        }
    }
}
