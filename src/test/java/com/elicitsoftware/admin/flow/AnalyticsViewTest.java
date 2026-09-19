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
import com.vaadin.flow.component.html.Anchor;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Analytics screen with analytics configured (UC-020 main flow, steps 4 and 6): the
 * dashboard mount is present and the "Open in Superset" link points at the configured
 * Superset in a new tab. The embedding itself happens in the browser and is verified end to
 * end against the local stack (plan section 7).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestSecurity(user = "uc020.analyst", roles = {"elicit_analytics"})
class AnalyticsViewTest extends QuarkusBrowserlessTest {

    private AnalyticsView attach() {
        AnalyticsView view = CDI.current().select(AnalyticsView.class).get();
        UI.getCurrent().add(view);
        return view;
    }

    /** UC-020 step 6: the link opens the configured Superset dashboard in a new tab. */
    @Test
    void openInSupersetLinksToConfiguredSuperset() {
        AnalyticsView view = attach();
        Anchor link = view.getOpenInSuperset();

        assertTrue(link.isVisible());
        assertEquals("http://superset.test:8088/superset/dashboard/survey-operations/", link.getHref());
        assertEquals("_blank", link.getTarget().orElse(null));
        assertEquals("Open in Superset", link.getText());
    }

    /** UC-020 step 4: the dashboard mount is rendered and no notice is shown. */
    @Test
    void dashboardMountIsPresentWithoutNotice() {
        AnalyticsView view = attach();

        assertTrue(find(com.vaadin.flow.component.html.Div.class, view).all().stream()
                .anyMatch(div -> AnalyticsView.MOUNT_ID.equals(div.getId().orElse(null)) && div.isVisible()),
                "the dashboard mount must be present and visible");
        assertFalse(view.getNotice().isVisible(), "no notice while the dashboard is expected to load");
    }
}
