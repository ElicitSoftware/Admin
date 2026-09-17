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
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.RouterLink;
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
 * UC-019 Surface Missing Survey Definition: the layout-wide banner and the per-view
 * empty states.
 * <p>
 * Whether a survey is installed is controlled through {@link SwitchablePresence},
 * installed over the real {@link SurveyDefinitionPresenceCheck} with
 * {@link QuarkusMock}. The shared test database cannot be relied on for this: other
 * test classes import surveys into it, so its survey count depends on execution order.
 * The real check's own query is covered by {@code SurveyDefinitionPresenceCheckTest}.
 * <p>
 * Components are attached to the test {@link UI} rather than reached by route, because
 * route navigation is unavailable under {@code @QuarkusTest} (see
 * {@code UnauthorizedViewTest}). The layout is therefore driven through
 * {@link MainLayout#showRouterLayoutContent}, and the views through
 * {@code refreshMissingSurveyNotice()}, which is what their enter hooks call.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class MissingSurveyWarningTest extends QuarkusBrowserlessTest {

    /**
     * Stand-in presence check whose answer a test can flip mid-test, to show that the
     * console re-reads the state rather than remembering it (BR-076).
     */
    static final class SwitchablePresence extends SurveyDefinitionPresenceCheck {
        volatile boolean installed;

        @Override
        public boolean isSurveyInstalled() {
            return installed;
        }
    }

    private SwitchablePresence presence;

    @BeforeEach
    void installSwitchablePresence() {
        presence = new SwitchablePresence();
        QuarkusMock.installMockForType(presence, SurveyDefinitionPresenceCheck.class);
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

    private static long countId(Component root, String id) {
        long self = root.getId().filter(id::equals).isPresent() ? 1 : 0;
        return self + root.getChildren().mapToLong(child -> countId(child, id)).sum();
    }

    // --- Layout banner -----------------------------------------------------------------

    /** UC-019 step 3: with no survey installed, the layout shows the banner. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void layoutShowsBannerWhenNoSurveyIsInstalled() {
        seedSessionUser("warning.admin");
        presence.installed = false;
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());

        assertTrue(containsId(layout.getContent(), MissingSurveyNotice.BANNER_ID),
                "the banner should be shown while no survey is installed");
    }

    /** UC-019 A1: with a survey installed, the layout shows no banner. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void layoutShowsNoBannerWhenASurveyIsInstalled() {
        seedSessionUser("warning.admin");
        presence.installed = true;
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());

        assertFalse(containsId(layout.getContent(), MissingSurveyNotice.BANNER_ID),
                "no banner should be shown once a survey is installed");
        assertEquals("routed-content", layout.getContent().getId().orElse(null),
                "with a survey installed the routed view should be the layout content itself, unwrapped");
    }

    /** UC-019 BR-077: the banner is added alongside the routed view, never in place of it. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void bannerDoesNotReplaceTheRoutedView() {
        seedSessionUser("warning.admin");
        presence.installed = false;
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());

        assertTrue(containsId(layout.getContent(), "routed-content"),
                "the routed view must still be shown beneath the banner");
    }

    /** UC-019 BR-076: the banner follows the installed state across navigations, without a restart. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void bannerFollowsTheInstalledStateAcrossNavigations() {
        seedSessionUser("warning.admin");
        MainLayout layout = attachLayout();

        presence.installed = false;
        layout.showRouterLayoutContent(routedContent());
        assertTrue(containsId(layout.getContent(), MissingSurveyNotice.BANNER_ID));

        presence.installed = true;
        layout.showRouterLayoutContent(routedContent());
        assertFalse(containsId(layout.getContent(), MissingSurveyNotice.BANNER_ID),
                "applying a definition should clear the banner on the next navigation");

        presence.installed = false;
        layout.showRouterLayoutContent(routedContent());
        assertTrue(containsId(layout.getContent(), MissingSurveyNotice.BANNER_ID),
                "removing the last survey should bring the banner back (A4)");
    }

    // --- Remedy link (BR-078) -------------------------------------------------------------

    /** UC-019 step 5 / BR-078: an administrator is offered a route to Apply Survey Definition. */
    @Test
    @TestSecurity(user = "warning.admin", roles = {"elicit_admin"})
    void administratorIsOfferedTheRouteToApplySurveyDefinition() {
        Component banner = MissingSurveyNotice.banner(true);
        UI.getCurrent().add(banner);

        List<RouterLink> links = find(RouterLink.class, banner).all();
        assertEquals(1, links.size(), "an administrator should be offered exactly one route");
        assertEquals("Apply a survey definition", links.get(0).getText());
        assertTrue(links.get(0).getHref().contains("survey-apply"),
                "the route should lead to Apply Survey Definition, was: " + links.get(0).getHref());
    }

    /** UC-019 A3 / BR-078: a user who cannot apply a definition is not offered the route. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void nonAdministratorIsNotOfferedTheRoute() {
        seedSessionUser("warning.user");
        presence.installed = false;
        MainLayout layout = attachLayout();

        layout.showRouterLayoutContent(routedContent());

        assertTrue(containsId(layout.getContent(), MissingSurveyNotice.BANNER_ID),
                "a non-administrator is still told that no survey is installed");
        assertTrue(find(RouterLink.class, layout.getContent()).all().isEmpty(),
                "a non-administrator must not be offered a route they cannot take");
    }

    /** UC-019: both notices announce themselves to assistive technology without interrupting. */
    @Test
    void noticesCarryTheStatusRole() {
        assertEquals("status", MissingSurveyNotice.banner(true).getElement().getAttribute("role"));
        assertEquals("status", MissingSurveyNotice.emptyState(false).getElement().getAttribute("role"));
    }

    // --- Per-view empty states ------------------------------------------------------------

    /** UC-019 step 5: with no survey installed, subject search explains its empty state. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void searchViewExplainsItsEmptyStateWhenNoSurveyIsInstalled() {
        seedSessionUser("warning.user");
        presence.installed = false;
        SearchView view = CDI.current().select(SearchView.class).get();
        UI.getCurrent().add(view);

        // SearchView's enter hook does nothing but this refresh, so a null event is safe here
        // and proves the hook is wired rather than just the helper.
        view.beforeEnter(null);

        assertTrue(containsId(view, MissingSurveyNotice.EMPTY_STATE_ID),
                "subject search should explain why it has nothing to show");
    }

    /** UC-019 A1: with a survey installed, subject search shows no explanation. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void searchViewShowsNoExplanationWhenASurveyIsInstalled() {
        seedSessionUser("warning.user");
        presence.installed = true;
        SearchView view = CDI.current().select(SearchView.class).get();
        UI.getCurrent().add(view);

        view.beforeEnter(null);

        assertFalse(containsId(view, MissingSurveyNotice.EMPTY_STATE_ID));
    }

    /** UC-019 BR-077: subject search stays usable while its explanation is shown. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void searchViewKeepsItsGridWhileExplaining() {
        seedSessionUser("warning.user");
        presence.installed = false;
        SearchView view = CDI.current().select(SearchView.class).get();
        UI.getCurrent().add(view);

        view.refreshMissingSurveyNotice();

        assertFalse(find(Grid.class, view).all().isEmpty(),
                "the explanation is added to the view, it must not remove the subject grid");
    }

    /** UC-019 BR-076: repeated entries neither stack explanations nor keep a stale one. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void searchViewExplanationIsReplacedNotStacked() {
        seedSessionUser("warning.user");
        SearchView view = CDI.current().select(SearchView.class).get();
        UI.getCurrent().add(view);

        presence.installed = false;
        view.refreshMissingSurveyNotice();
        view.refreshMissingSurveyNotice();
        assertEquals(1, countId(view, MissingSurveyNotice.EMPTY_STATE_ID),
                "entering twice must not stack two explanations");

        presence.installed = true;
        view.refreshMissingSurveyNotice();
        assertEquals(0, countId(view, MissingSurveyNotice.EMPTY_STATE_ID),
                "applying a definition should clear the explanation on the next entry");
    }

    /** UC-019 step 5: with no survey installed, subject registration explains its empty state. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void registerViewExplainsItsEmptyStateWhenNoSurveyIsInstalled() {
        seedSessionUser("warning.user");
        presence.installed = false;
        RegisterView view = CDI.current().select(RegisterView.class).get();
        UI.getCurrent().add(view);

        view.refreshMissingSurveyNotice();

        assertTrue(containsId(view, MissingSurveyNotice.EMPTY_STATE_ID),
                "subject registration should explain why it has nothing to register against");
    }

    /** UC-019 A1: with a survey installed, subject registration shows no explanation. */
    @Test
    @TestSecurity(user = "warning.user", roles = {"elicit_user"})
    void registerViewShowsNoExplanationWhenASurveyIsInstalled() {
        seedSessionUser("warning.user");
        presence.installed = true;
        RegisterView view = CDI.current().select(RegisterView.class).get();
        UI.getCurrent().add(view);

        view.refreshMissingSurveyNotice();

        assertFalse(containsId(view, MissingSurveyNotice.EMPTY_STATE_ID));
    }
}
