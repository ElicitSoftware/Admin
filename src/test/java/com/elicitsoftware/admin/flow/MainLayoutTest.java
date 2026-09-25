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

import com.elicitsoftware.model.User;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link MainLayout}'s role-aware navigation.
 *
 * <p>Traceability: UC-001 (Authenticate and Access the Admin Console). {@code MainLayout} builds
 * a different drawer depending on whether a user is present in the Vaadin session and whether
 * they hold {@code elicit_admin}: authenticated users get "Search Subjects"/"Register Subjects"
 * (plus an Admin section for admins), while an absent session falls back to a Logout-only
 * drawer. The view is obtained through CDI so its {@code @PostConstruct} runs, after seeding the
 * session the same way {@code SearchViewTest} does (bypassing {@code UiSessionLogin}'s own DB
 * lookup).</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class MainLayoutTest extends QuarkusBrowserlessTest {

    private MainLayout attachLayout() {
        MainLayout layout = CDI.current().select(MainLayout.class).get();
        UI.getCurrent().add(layout);
        return layout;
    }

    private boolean hasNavItem(MainLayout layout, String text) {
        return find(SideNavItem.class, layout).all().stream()
                .anyMatch(item -> text.equals(item.getLabel()));
    }

    /** UC-001: an authenticated admin sees the Admin section (Departments/Message Templates/Users). */
    @Test
    @TestSecurity(user = "mainlayout.admin", roles = {"elicit_admin"})
    void adminSeesAdminSection() {
        User user = new User();
        user.setId(1);
        user.setUsername("mainlayout.admin");
        user.setActive(true);
        VaadinSession.getCurrent().setAttribute("user", user);

        MainLayout layout = attachLayout();

        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.searchSubjects")));
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.registerSubjects")));
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.admin")), "an admin should see the Admin section");
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.importRespondent")), "an admin should see Import Respondent");
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.applySurveyDefinition")), "an admin should see Apply Survey Definition");
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.exportSurveyDefinition")), "an admin should see Export Survey Definition");
        // UC-020..UC-025 and FR-026: the System section and its six entries.
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.system")), "an admin should see the System section");
        for (String entry : List.of("systemOverview", "systemDatabase", "systemBranding", "systemEmail", "systemConnections", "systemOidc")) {
            assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav." + entry)), "an admin should see System > " + entry);
        }
        // UC-029 BR-006: the manual is offered to both console roles, so it sits outside the
        // administrator-only sections asserted above.
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.manual")),
                "an admin should be offered the manual (UC-029)");
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.logout")));
    }

    /** UC-001: an authenticated non-admin does not see the Admin section. */
    @Test
    @TestSecurity(user = "mainlayout.user", roles = {"elicit_user"})
    void nonAdminDoesNotSeeAdminSection() {
        User user = new User();
        user.setId(2);
        user.setUsername("mainlayout.user");
        user.setActive(true);
        VaadinSession.getCurrent().setAttribute("user", user);

        MainLayout layout = attachLayout();

        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.searchSubjects")));
        assertFalse(hasNavItem(layout, layout.getTranslation("mainLayout.nav.admin")), "a non-admin must not see the Admin section");
        assertFalse(hasNavItem(layout, layout.getTranslation("mainLayout.nav.system")), "a non-admin must not see the System section");
        assertFalse(hasNavItem(layout, layout.getTranslation("mainLayout.nav.systemOidc")), "a non-admin must not see System > OIDC");
        assertFalse(hasNavItem(layout, layout.getTranslation("mainLayout.nav.importRespondent")), "a non-admin must not see Import Respondent");
        assertFalse(hasNavItem(layout, layout.getTranslation("mainLayout.nav.exportSurveyDefinition")),
                "a non-admin must not see Export Survey Definition");
        // UC-029 BR-006: one manual serves both roles -- this is the entry that must NOT follow
        // the Admin section out of the drawer.
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.manual")),
                "a non-admin should still be offered the manual (UC-029 BR-006)");
    }

    /** UC-001 A1: with no user in the session, the drawer falls back to a Logout-only nav. */
    @Test
    @TestSecurity(user = "mainlayout.orphan", roles = {})
    void noSessionUserFallsBackToLogoutOnlyNav() {
        VaadinSession.getCurrent().setAttribute("user", null);

        MainLayout layout = attachLayout();

        assertFalse(hasNavItem(layout, layout.getTranslation("mainLayout.nav.searchSubjects")),
                "with no session user, the full nav must not be built");
        assertTrue(hasNavItem(layout, layout.getTranslation("mainLayout.nav.logout")));
        // Exactly one item (Logout) in the fallback nav.
        SideNav nav = find(SideNav.class, layout).single();
        assertTrue(find(SideNavItem.class, nav).all().size() == 1);
    }

    /** afterNavigation() (registered via onAttach) runs without error. */
    @Test
    @TestSecurity(user = "mainlayout.scroll", roles = {"elicit_user"})
    void afterNavigationDoesNotThrow() {
        User user = new User();
        user.setId(3);
        user.setUsername("mainlayout.scroll");
        user.setActive(true);
        VaadinSession.getCurrent().setAttribute("user", user);

        MainLayout layout = attachLayout();
        // In a real app the router populates this via showRouterLayoutContent(); attaching the
        // layout directly (no route navigation, see UsersViewTest) leaves it null.
        layout.setContent(new com.vaadin.flow.component.html.Div());

        assertDoesNotThrow(() -> layout.afterNavigation(null));
    }
}
