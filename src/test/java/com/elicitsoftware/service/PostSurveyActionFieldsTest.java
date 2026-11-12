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
package com.elicitsoftware.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-018 BR-134 and BR-135, for the install and the update alike: the shared field rules. */
class PostSurveyActionFieldsTest {

    @Test
    void br134_aRecordWithoutTheNewFieldsReadsAsPostWithoutTheExternalId() {
        String[] six = {"1", "k", "Notify", "d", "https://x.example/n", "1"};
        assertEquals("POST", SurveyDefinitionFileFields.postSurveyActionMethod(six));
        assertFalse(SurveyDefinitionFileFields.postSurveyActionSendsExternalId(six));
        String[] eight = {"1", "k", "Notify", "d", "https://x.example/n", "1", "put", "true"};
        assertEquals("PUT", SurveyDefinitionFileFields.postSurveyActionMethod(eight));
        assertTrue(SurveyDefinitionFileFields.postSurveyActionSendsExternalId(eight));
    }

    @Test
    void br135_onlyExternalIdActionsAtANonHttpsAddressAreNamed() {
        String file = String.join("\n",
                "# ELICIT_SURVEY_EXPORT_V1",
                "post_survey_actions: 1|a|Plain|d|http://x.example/n|1|POST|false",
                "post_survey_actions: 2|b|Secure|d|https://x.example/{externalId}|2|POST|true",
                "post_survey_actions: 3|c|Leaky|d|http://x.example/{externalId}|3|GET|true",
                "post_survey_actions: 4|d|Old|d|http://x.example/old|4");
        assertEquals(List.of("Leaky"), SurveyDefinitionFileFields.externalIdActionsWithoutHttps(file));
    }
}
