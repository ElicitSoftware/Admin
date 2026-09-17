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
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-019 Surface Missing Survey Definition: the query the console asks on every
 * navigation, and the startup warning that predates it.
 * <p>
 * The shared test database may or may not hold surveys depending on which test classes
 * ran first, so these tests assert against the database's current state rather than
 * assuming it is empty. The empty branch as the console presents it is covered
 * deterministically by {@code MissingSurveyWarningTest}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SurveyDefinitionPresenceCheckTest {

    @Inject
    SurveyDefinitionPresenceCheck presence;

    /** UC-019 step 2: the answer agrees with the surveys actually stored. */
    @Test
    void isSurveyInstalledAgreesWithTheStoredSurveyCount() {
        // A lambda, not Survey::count: the method reference resolves to the un-enhanced
        // PanacheEntityBase.count(), which Panache only rewrites on the entity itself.
        long stored = QuarkusTransaction.requiringNew().call(() -> Survey.count());

        assertEquals(stored > 0, presence.isSurveyInstalled());
    }

    /** UC-019 BR-076: a survey stored after startup is seen at once, with no restart. */
    @Test
    @TestTransaction
    void aNewlyStoredSurveyIsSeenImmediately() {
        Survey survey = new Survey();
        survey.surveyKey = UUID.randomUUID();
        survey.displayOrder = 1;
        survey.name = "UC-019 presence probe";
        survey.persist();

        assertTrue(presence.isSurveyInstalled(),
                "a survey stored in this transaction should be reported as installed");
    }

    /** UC-019: the startup warning runs against the live database without failing. */
    @Test
    void startupCheckRunsWithoutFailing() {
        assertDoesNotThrow(() -> presence.warnWhenNoSurveyDefined());
    }
}
