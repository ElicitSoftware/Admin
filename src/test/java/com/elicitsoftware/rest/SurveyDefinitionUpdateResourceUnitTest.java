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

import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.service.ReportingSchemaRebuildClient;
import com.elicitsoftware.service.SurveyDefinitionUpdateService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-017: Update Survey Definition — how {@link SurveyDefinitionUpdateResource} validates the
 * request, turns the update's result into an HTTP answer (404 for an unknown target, 400 for a
 * refused file such as a key mismatch, BR-063, or a regression, BR-069) and asks Survey to rebuild
 * the reporting schema only after a successful update (UC-018 BR-107, BR-108). The update service
 * and the rebuild client are stubs; nothing is booted.
 */
class SurveyDefinitionUpdateResourceUnitTest {

    SurveyDefinitionUpdateResource resource;
    SurveyDefinitionUpdateService.UpdateResult result;
    RuntimeException failure;
    final List<Object> updated = new ArrayList<>();
    final List<UUID> rebuilt = new ArrayList<>();
    ReportingSchemaRebuildClient.Outcome outcome;

    @BeforeEach
    void setUp() {
        outcome = new ReportingSchemaRebuildClient.Outcome(ReportingSchemaRebuildClient.Status.SKIPPED, "");
        resource = new SurveyDefinitionUpdateResource();
        resource.surveyDefinitionUpdateService = new SurveyDefinitionUpdateService() {
            @Override
            public UpdateResult updateFromFile(InputStream inputStream, String fileName, Integer targetSurveyId) {
                updated.add(inputStream);
                updated.add(fileName);
                updated.add(targetSurveyId);
                if (failure != null) {
                    throw failure;
                }
                return result;
            }
        };
        resource.reportingSchemaRebuildClient = new ReportingSchemaRebuildClient() {
            @Override
            public Outcome rebuild(UUID surveyKey) {
                rebuilt.add(surveyKey);
                return outcome;
            }
        };
    }

    private static MultipartBody body(InputStream file, String surveyId) {
        MultipartBody body = new MultipartBody();
        body.file = file;
        body.fileName = "f.elicit";
        body.surveyId = surveyId;
        return body;
    }

    private static InputStream file() {
        return new ByteArrayInputStream(new byte[]{1});
    }

    private static SurveyDefinitionUpdateResource.UpdateResponse entity(Response response) {
        return (SurveyDefinitionUpdateResource.UpdateResponse) response.getEntity();
    }

    @Test
    void given_noFile_when_updated_then_badRequest() {
        Response response = resource.updateSurvey(body(null, "3"));
        assertEquals(400, response.getStatus());
        assertEquals("No file provided in the 'file' field", entity(response).getMessage());
        assertFalse(entity(response).isSuccess());
        assertTrue(updated.isEmpty());
    }

    @Test
    void given_noUsableTargetId_when_updated_then_badRequest() {
        for (String surveyId : new String[]{null, "", "   ", "three", "3.5"}) {
            Response response = resource.updateSurvey(body(file(), surveyId));
            assertEquals(400, response.getStatus(), String.valueOf(surveyId));
            assertEquals("No target survey selected in the 'surveyId' field", entity(response).getMessage());
            assertNull(entity(response).getCounts());
        }
        assertTrue(updated.isEmpty());
    }

    @Test
    void given_aSuccessfulUpdate_when_answered_then_okWithCountsAndTheRebuildRequested() {
        UUID key = UUID.randomUUID();
        Map<String, SurveyDefinitionUpdateService.TableUpdateCounts> counts =
                Map.of("steps", new SurveyDefinitionUpdateService.TableUpdateCounts(1, 0, 2, 0));
        result = new SurveyDefinitionUpdateService.UpdateResult(true, List.of(), counts, key);
        InputStream in = file();
        Response response = resource.updateSurvey(body(in, " 42 "));

        assertEquals(200, response.getStatus());
        assertTrue(entity(response).isSuccess());
        assertEquals("Update completed successfully", entity(response).getMessage());
        assertEquals(counts, entity(response).getCounts());
        assertNull(entity(response).getReporting(), "a skipped rebuild says nothing");
        assertEquals(List.of(key), rebuilt);
        assertSame(in, updated.get(0));
        assertEquals("f.elicit", updated.get(1));
        assertEquals(42, updated.get(2));
    }

    @Test
    void given_aRebuiltSchema_when_answered_then_theRebuildLineIsReported() {
        result = new SurveyDefinitionUpdateService.UpdateResult(true, List.of(), Map.of(), UUID.randomUUID());
        outcome = new ReportingSchemaRebuildClient.Outcome(ReportingSchemaRebuildClient.Status.REBUILT, "ok");
        Response response = resource.updateSurvey(body(file(), "42"));
        assertEquals(Translations.get("reportingRebuild.rebuilt"), entity(response).getReporting());
    }

    @Test
    void given_anUnknownTarget_when_answered_then_notFound() {
        result = new SurveyDefinitionUpdateService.UpdateResult(false,
                List.of(Translations.get("definition.targetNotFound", "42")), null);
        Response response = resource.updateSurvey(body(file(), "42"));
        assertEquals(404, response.getStatus());
        assertEquals(Translations.get("definition.targetNotFound", "42"), entity(response).getMessage());
        assertTrue(rebuilt.isEmpty());
    }

    @Test
    void given_aRefusedFile_when_answered_then_badRequestWithEveryError() {
        result = new SurveyDefinitionUpdateService.UpdateResult(false, List.of("mismatch", "and more"), null);
        Response response = resource.updateSurvey(body(file(), "42"));
        assertEquals(400, response.getStatus());
        assertFalse(entity(response).isSuccess());
        assertEquals("mismatch; and more", entity(response).getMessage());
        assertTrue(rebuilt.isEmpty());
    }

    @Test
    void given_anUnexpectedFailure_when_answered_then_aServerErrorWithAReference() {
        failure = new RuntimeException("Update failed at line 9: duplicate key");
        Response response = resource.updateSurvey(body(file(), "42"));
        assertEquals(500, response.getStatus());
        String prefix = "Update failed due to an unexpected error. Reference: ";
        String message = entity(response).getMessage();
        assertTrue(message.startsWith(prefix), message);
        UUID.fromString(message.substring(prefix.length()));
        assertFalse(entity(response).isSuccess());
    }

    @Test
    void given_theResponseBean_when_populated_then_itHoldsWhatWasSet() {
        SurveyDefinitionUpdateResource.UpdateResponse r = new SurveyDefinitionUpdateResource.UpdateResponse();
        r.setSuccess(true);
        r.setMessage("m");
        r.setCounts(Map.of());
        r.setReporting("rep");
        assertTrue(r.isSuccess());
        assertEquals("m", r.getMessage());
        assertEquals(Map.of(), r.getCounts());
        assertEquals("rep", r.getReporting());
    }
}
