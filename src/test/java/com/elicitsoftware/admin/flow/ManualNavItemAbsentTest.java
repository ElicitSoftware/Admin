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

import com.elicitsoftware.model.User;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * UC-029 A1: an image built without the manual leaves the entry out of the drawer rather than
 * offering a link that would answer 404.
 * <p>
 * This needs its own class because the condition is a configuration override and a Quarkus test
 * profile is per-class: {@link MainLayoutTest} asserts the opposite case, that both console roles
 * <em>are</em> offered the manual (UC-029 BR-006), and the two cannot share a profile. The
 * companion assertion for the blocking no-department notice lives in {@code ManualNavigationTest};
 * this one is about the drawer.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(ManualNavItemAbsentTest.NoManualProfile.class)
class ManualNavItemAbsentTest extends QuarkusBrowserlessTest {

    /** Points the manual at a resource this build does not carry, as an image built with SKIP_MANUAL=1 would. */
    public static class NoManualProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("admin.manual.resource", "manual/no-such-manual.pdf");
        }
    }

    /** UC-029 A1: no manual packaged, so no entry -- for an administrator. */
    @Test
    @TestSecurity(user = "nomanual.admin", roles = {"elicit_admin"})
    void administratorDrawerOmitsTheManualWhenTheImageCarriesNone() {
        assertNoManualEntry(3, "nomanual.admin");
    }

    /** UC-029 A1 with BR-006: the entry is absent for the other role too, not merely role-gated away. */
    @Test
    @TestSecurity(user = "nomanual.user", roles = {"elicit_user"})
    void userDrawerOmitsTheManualWhenTheImageCarriesNone() {
        assertNoManualEntry(4, "nomanual.user");
    }

    private void assertNoManualEntry(int id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setActive(true);
        VaadinSession.getCurrent().setAttribute("user", user);

        MainLayout layout = CDI.current().select(MainLayout.class).get();
        UI.getCurrent().add(layout);

        String label = layout.getTranslation("mainLayout.nav.manual");
        assertFalse(
                find(SideNavItem.class, layout).all().stream()
                        .anyMatch(item -> label.equals(item.getLabel())),
                "a build carrying no manual must not offer the entry (UC-029 A1)");
    }
}
