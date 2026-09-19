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
import com.elicitsoftware.test.AnalyticsDisabledTestProfile;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-020/A1: without analytics configuration the Analytics item is absent even for an analyst. */
@QuarkusTest
@TestProfile(AnalyticsDisabledTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
class MainLayoutAnalyticsDisabledTest extends QuarkusBrowserlessTest {

    /** UC-020/A1/BR-082: no configuration, no menu item, everything else unchanged. */
    @Test
    @TestSecurity(user = "mainlayout.analyst.off", roles = {"elicit_analytics"})
    void analyticsItemHiddenWhenNotConfigured() {
        // Touch the UI-scoped login bean first: its first use clears an unknown principal's
        // session user (see MainLayoutTest.seedSessionUser).
        CDI.current().select(UiSessionLogin.class).get().getUser();
        User user = new User();
        user.setId(6);
        user.setUsername("mainlayout.analyst.off");
        user.setActive(true);
        VaadinSession.getCurrent().setAttribute("user", user);

        MainLayout layout = CDI.current().select(MainLayout.class).get();
        UI.getCurrent().add(layout);

        boolean analytics = find(SideNavItem.class, layout).all().stream()
                .anyMatch(item -> "Analytics".equals(item.getLabel()));
        assertFalse(analytics, "the Analytics item must be hidden when analytics is not configured");
        assertTrue(find(SideNavItem.class, layout).all().stream().anyMatch(item -> "Search Subjects".equals(item.getLabel())),
                "the rest of the nav is unchanged");
        assertTrue(find(SideNavItem.class, layout).all().stream().anyMatch(item -> "Logout".equals(item.getLabel())));
    }
}
