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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console warns administrators while the seeded default accounts exist.
 *
 * <p>Traceability: UC-021 (Warn About Default Accounts), A1, A2, BR-086, BR-087.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DefaultAccountWarningTest extends QuarkusBrowserlessTest {

    /** Stand-in whose answer a test can change mid-test (BR-086). */
    static final class SwitchableAccounts extends DefaultAccountCheck {
        volatile List<String> accounts = List.of();

        @Override
        public List<String> findDefaultAccounts() {
            return accounts;
        }
    }

    /** A survey is installed throughout, so only the default-account banner is in play. */
    static final class InstalledPresence extends SurveyDefinitionPresenceCheck {
        @Override
        public boolean isSurveyInstalled() {
            return true;
        }
    }

    private SwitchableAccounts accounts;

    @BeforeEach
    void installStubs() {
        accounts = new SwitchableAccounts();
        QuarkusMock.installMockForType(accounts, DefaultAccountCheck.class);
        QuarkusMock.installMockForType(new InstalledPresence(), SurveyDefinitionPresenceCheck.class);
    }

    private static void seedSessionUser(String username) {
        User user = new User();
        user.setId(1);
        user.setUsername(username);
        user.setActive(true);
        Department department = new Department();
        department.id = 1;
        department.name = "Warning Dept";
        Set<Department> departments = new HashSet<>();
        departments.add(department);
        user.setDepartments(departments);
        VaadinSession.getCurrent().setAttribute("user", user);
    }

    private MainLayout attachLayout() {
        MainLayout layout = CDI.current().select(MainLayout.class).get();
        UI.getCurrent().add(layout);
        return layout;
    }

    private static Div routedContent() {
        Div content = new Div();
        content.setId("routed-content");
        return content;
    }

    private static boolean containsId(Component root, String id) {
        if (root.getId().filter(id::equals).isPresent()) {
            return true;
        }
        return root.getChildren().anyMatch(child -> containsId(child, id));
    }

    /** UC-021 step 4: an administrator sees the banner while a seeded account exists. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void adminSeesBannerWhileSeededAccountsExist() {
        seedSessionUser("warning.admin");
        accounts.accounts = List.of("admin", "user");
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());

        assertTrue(containsId(layout.getContent(), DefaultAccountNotice.BANNER_ID),
                "the banner should be shown while a seeded account exists");
        assertTrue(containsId(layout.getContent(), "routed-content"),
                "the routed view must still be shown beneath the banner (BR-087)");
    }

    /** UC-021 A1: with no seeded account, the routed view is the content itself. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void noBannerOnceAccountsAreRenamed() {
        seedSessionUser("warning.admin");
        accounts.accounts = List.of();
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());

        assertFalse(containsId(layout.getContent(), DefaultAccountNotice.BANNER_ID));
        assertEquals("routed-content", layout.getContent().getId().orElse(null),
                "without warnings the routed view should be the layout content itself, unwrapped");
    }

    /** UC-021 A2: a non-administrator is not warned, since only an administrator can rename. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void nonAdminIsNotWarned() {
        seedSessionUser("warning.user");
        accounts.accounts = List.of("admin", "user");
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());

        assertFalse(containsId(layout.getContent(), DefaultAccountNotice.BANNER_ID),
                "a non-admin must not see the default-account banner");
    }

    /** UC-021 BR-086: the banner follows the stored state across navigations, without a restart. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void bannerFollowsTheStoredStateAcrossNavigations() {
        seedSessionUser("warning.admin");
        MainLayout layout = attachLayout();

        accounts.accounts = List.of("admin");
        layout.showRouterLayoutContent(routedContent());
        assertTrue(containsId(layout.getContent(), DefaultAccountNotice.BANNER_ID));

        accounts.accounts = List.of();
        layout.showRouterLayoutContent(routedContent());
        assertFalse(containsId(layout.getContent(), DefaultAccountNotice.BANNER_ID),
                "renaming the account must clear the banner on the next navigation");
    }
}
