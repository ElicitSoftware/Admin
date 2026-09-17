package com.elicitsoftware.service;

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

import com.elicitsoftware.model.Survey;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Warns at startup when the database holds no survey definition.
 * <p>
 * A freshly migrated deployment has an empty {@code survey.surveys} until a definition is
 * imported (UC-014, {@link SurveyDefinitionImportService}). Admin still starts and its
 * screens still load, so nothing about the running application says why respondent,
 * message and report screens have nothing to work with -- this makes that state explicit
 * instead of leaving it to be inferred from empty grids.
 * <p>
 * Logged at WARN deliberately: {@code quarkus.log.level} defaults to WARN, so an INFO
 * message would be invisible in container deployments, which is exactly where a
 * freshly-migrated empty database shows up.
 */
@ApplicationScoped
public class SurveyDefinitionPresenceCheck {

    /**
     * Default constructor for CDI.
     */
    public SurveyDefinitionPresenceCheck() {
        // CDI managed bean
    }

    /**
     * Counts installed survey definitions and warns when there are none.
     * <p>
     * {@code @Startup} sits on the method rather than the class so it runs through the
     * {@code @Transactional} interceptor -- the Panache count needs an active transaction,
     * and interceptors do not apply to CDI lifecycle callbacks.
     */
    @Startup
    @Transactional
    void warnWhenNoSurveyDefined() {
        long surveys = Survey.count();
        if (surveys == 0) {
            Log.warn("No survey is defined in the database (survey.surveys is empty). "
                    + "Import a survey definition through Apply Survey Definition, or "
                    + "POST /api/secured/survey/apply, before registering respondents.");
        } else {
            Log.debugf("Survey definitions installed: %d", surveys);
        }
    }
}
