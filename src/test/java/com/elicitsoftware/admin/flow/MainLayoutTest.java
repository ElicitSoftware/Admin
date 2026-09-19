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

    /**
     * Puts a session user in place the way a signed-in browser would have one. The UI-scoped
     * {@link UiSessionLogin} looks the principal up in the database the first time it is used
     * in a UI and, finding no row for a test principal, clears the session user -- so it is
     * touched first and the user set afterwards. Without this, whichever test runs first in
     * a fresh UI scope sees the Logout-only fallback nav regardless of its roles.
     */
    private void seedSessionUser(long id, String username) {
        CDI.current().select(UiSessionLogin.class).get().getUser();
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setActive(true);
        VaadinSession.getCurrent().setAttribute("user", user);
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

        assertTrue(hasNavItem(layout, "Search Subjects"));
        assertTrue(hasNavItem(layout, "Register Subjects"));
        assertTrue(hasNavItem(layout, "Admin"), "an admin should see the Admin section");
        assertTrue(hasNavItem(layout, "Import Respondent"), "an admin should see Import Respondent");
        assertTrue(hasNavItem(layout, "Logout"));
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

        assertTrue(hasNavItem(layout, "Search Subjects"));
        assertFalse(hasNavItem(layout, "Admin"), "a non-admin must not see the Admin section");
        assertFalse(hasNavItem(layout, "Import Respondent"), "a non-admin must not see Import Respondent");
    }

    /** UC-001 A1: with no user in the session, the drawer falls back to a Logout-only nav. */
    @Test
    @TestSecurity(user = "mainlayout.orphan", roles = {})
    void noSessionUserFallsBackToLogoutOnlyNav() {
        VaadinSession.getCurrent().setAttribute("user", null);

        MainLayout layout = attachLayout();

        assertFalse(hasNavItem(layout, "Search Subjects"),
                "with no session user, the full nav must not be built");
        assertTrue(hasNavItem(layout, "Logout"));
        // Exactly one item (Logout) in the fallback nav.
        SideNav nav = find(SideNav.class, layout).single();
        assertTrue(find(SideNavItem.class, nav).all().size() == 1);
    }

    /** UC-020: an analyst (no ladder role) sees the Analytics item when analytics is configured. */
    @Test
    @TestSecurity(user = "mainlayout.analyst", roles = {"elicit_analytics"})
    void analystSeesAnalyticsItem() {
        seedSessionUser(4, "mainlayout.analyst");

        MainLayout layout = attachLayout();

        assertTrue(hasNavItem(layout, "Analytics"), "an analyst should see the Analytics item");
        assertFalse(hasNavItem(layout, "Admin"), "the analytics role implies no admin rights");
    }

    /** UC-020/A2 + BR-080: neither elicit_user nor elicit_admin implies the Analytics item. */
    @Test
    @TestSecurity(user = "mainlayout.admin2", roles = {"elicit_admin", "elicit_user"})
    void ladderRolesDoNotSeeAnalyticsItem() {
        seedSessionUser(5, "mainlayout.admin2");

        MainLayout layout = attachLayout();

        assertFalse(hasNavItem(layout, "Analytics"), "elicit_admin must not imply the Analytics item");
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
