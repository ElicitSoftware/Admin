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

import com.elicitsoftware.test.AnalyticsDisabledTestProfile;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-020/A1: the Analytics screen reached by its address without configuration explains itself. */
@QuarkusTest
@TestProfile(AnalyticsDisabledTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
@TestSecurity(user = "uc020.analyst.off", roles = {"elicit_analytics"})
class AnalyticsViewDisabledTest extends QuarkusBrowserlessTest {

    /** UC-020/A1/BR-082: notice shown, no link, no mount. */
    @Test
    void notConfiguredShowsNoticeOnly() {
        AnalyticsView view = CDI.current().select(AnalyticsView.class).get();
        UI.getCurrent().add(view);

        assertTrue(view.getNotice().isVisible());
        assertEquals(AnalyticsView.NOT_CONFIGURED_TEXT, view.getNotice().getText());
        assertFalse(view.getOpenInSuperset().isVisible(), "no link when there is nowhere to go");
    }
}
