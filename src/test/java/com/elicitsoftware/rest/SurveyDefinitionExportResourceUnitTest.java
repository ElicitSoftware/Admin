package com.elicitsoftware.rest;

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

import com.elicitsoftware.service.SurveyDefinitionExportService;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-013: Export Survey Definition — how {@link SurveyDefinitionExportResource} answers: the file
 * as a named attachment (BR-081), 400 without an id and 404 for an unknown survey. The export
 * service is a stub; nothing is booted.
 */
class SurveyDefinitionExportResourceUnitTest {

    SurveyDefinitionExportResource resource;

    @BeforeEach
    void setUp() {
        resource = new SurveyDefinitionExportResource();
        resource.surveyDefinitionExportService = new SurveyDefinitionExportService() {
            @Override
            public String exportSurvey(Integer surveyId) {
                if (surveyId == 404) {
                    throw new IllegalArgumentException("Survey not found: 404");
                }
                return "# export of " + surveyId + " é";
            }
        };
    }

    @Test
    void given_noId_when_exported_then_badRequest() {
        Response response = resource.exportSurvey(null);
        assertEquals(400, response.getStatus());
        assertEquals("Missing required query parameter: id", response.getEntity());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
    }

    @Test
    void given_aSurvey_when_exported_then_theFileIsAnUtf8Attachment() {
        Response response = resource.exportSurvey(3);
        assertEquals(200, response.getStatus());
        assertArrayEquals("# export of 3 é".getBytes(StandardCharsets.UTF_8), (byte[]) response.getEntity());
        assertEquals("attachment; filename=\"survey_3_definition.elicit\"", response.getHeaderString("Content-Disposition"));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM_TYPE, response.getMediaType());
    }

    @Test
    void given_anUnknownSurvey_when_exported_then_notFoundWithTheReason() {
        Response response = resource.exportSurvey(404);
        assertEquals(404, response.getStatus());
        assertEquals("Survey not found: 404", response.getEntity());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
    }
}
