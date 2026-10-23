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
import com.elicitsoftware.service.SurveyDefinitionImportService;
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
 * UC-014: Import Survey Definition — how {@link SurveyDefinitionImportResource} turns the import's
 * result into an HTTP answer, and asks Survey to rebuild the reporting schema only after a
 * successful import (UC-018 BR-107), without letting a failed rebuild fail the import (BR-108).
 * The import service and the rebuild client are stubs; nothing is booted.
 */
class SurveyDefinitionImportResourceUnitTest {

    SurveyDefinitionImportResource resource;
    SurveyDefinitionImportService.ImportResult result;
    RuntimeException failure;
    final List<Object> imported = new ArrayList<>();
    final List<UUID> rebuilt = new ArrayList<>();
    ReportingSchemaRebuildClient.Outcome outcome;

    @BeforeEach
    void setUp() {
        outcome = new ReportingSchemaRebuildClient.Outcome(ReportingSchemaRebuildClient.Status.REBUILT, "done");
        resource = new SurveyDefinitionImportResource();
        resource.surveyDefinitionImportService = new SurveyDefinitionImportService() {
            @Override
            public ImportResult importFromFile(InputStream inputStream, String fileName) {
                imported.add(inputStream);
                imported.add(fileName);
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

    private static MultipartBody body(InputStream file) {
        MultipartBody body = new MultipartBody();
        body.file = file;
        body.fileName = "fhhs.elicit";
        return body;
    }

    private static SurveyDefinitionImportResource.ImportResponse entity(Response response) {
        return (SurveyDefinitionImportResource.ImportResponse) response.getEntity();
    }

    @Test
    void given_noFile_when_imported_then_badRequestAndNothingIsAttempted() {
        Response response = resource.importSurvey(body(null));
        assertEquals(400, response.getStatus());
        assertFalse(entity(response).isSuccess());
        assertEquals(0, entity(response).getRecordsImported());
        assertEquals("No file provided in the 'file' field", entity(response).getMessage());
        assertNull(entity(response).getCounts());
        assertTrue(imported.isEmpty());
        assertTrue(rebuilt.isEmpty());
    }

    @Test
    void given_aSuccessfulImport_when_answered_then_okWithCountsAndTheRebuildLine() {
        UUID key = UUID.randomUUID();
        result = new SurveyDefinitionImportService.ImportResult(true, 12, List.of(), Map.of("steps", 12), key);
        InputStream file = new ByteArrayInputStream(new byte[]{1});
        Response response = resource.importSurvey(body(file));

        assertEquals(200, response.getStatus());
        SurveyDefinitionImportResource.ImportResponse r = entity(response);
        assertTrue(r.isSuccess());
        assertEquals(12, r.getRecordsImported());
        assertEquals("Import completed successfully", r.getMessage());
        assertEquals(Map.of("steps", 12), r.getCounts());
        assertEquals(Translations.get("reportingRebuild.rebuilt"), r.getReporting());
        assertEquals(List.of(key), rebuilt);
        assertSame(file, imported.get(0));
        assertEquals("fhhs.elicit", imported.get(1));
    }

    @Test
    void given_aFailedRebuild_when_answered_then_theImportStillSucceedsAndSaysWhy() {
        result = new SurveyDefinitionImportService.ImportResult(true, 1, List.of(), Map.of(), UUID.randomUUID());
        outcome = new ReportingSchemaRebuildClient.Outcome(ReportingSchemaRebuildClient.Status.FAILED, "down");
        Response response = resource.importSurvey(body(new ByteArrayInputStream(new byte[0])));
        assertEquals(200, response.getStatus());
        assertEquals(Translations.get("reportingRebuild.notRebuilt", "down"), entity(response).getReporting());
    }

    @Test
    void given_aRejectedImport_when_answered_then_badRequestWithEveryErrorAndNoRebuild() {
        result = new SurveyDefinitionImportService.ImportResult(false, 0, List.of("one", "two"), Map.of("steps", 0));
        Response response = resource.importSurvey(body(new ByteArrayInputStream(new byte[0])));

        assertEquals(400, response.getStatus());
        assertFalse(entity(response).isSuccess());
        assertEquals("one; two", entity(response).getMessage());
        assertEquals(Map.of("steps", 0), entity(response).getCounts());
        assertNull(entity(response).getReporting());
        assertTrue(rebuilt.isEmpty());
    }

    @Test
    void given_anUnexpectedFailure_when_answered_then_aServerErrorWithAReferenceAndNoDetail() {
        failure = new IllegalStateException("constraint survey_pkey violated");
        Response response = resource.importSurvey(body(new ByteArrayInputStream(new byte[0])));

        assertEquals(500, response.getStatus());
        String message = entity(response).getMessage();
        String prefix = "Import failed due to an unexpected error. Reference: ";
        assertTrue(message.startsWith(prefix), message);
        UUID.fromString(message.substring(prefix.length()));
        assertFalse(message.contains("survey_pkey"));
        assertNull(entity(response).getCounts());
        assertTrue(rebuilt.isEmpty());
    }

    @Test
    void given_theResponseBean_when_populated_then_itHoldsWhatWasSet() {
        SurveyDefinitionImportResource.ImportResponse r = new SurveyDefinitionImportResource.ImportResponse();
        r.setSuccess(true);
        r.setRecordsImported(3);
        r.setMessage("m");
        r.setCounts(Map.of("a", 1));
        r.setReporting("rep");
        assertTrue(r.isSuccess());
        assertEquals(3, r.getRecordsImported());
        assertEquals("m", r.getMessage());
        assertEquals(Map.of("a", 1), r.getCounts());
        assertEquals("rep", r.getReporting());
    }
}
