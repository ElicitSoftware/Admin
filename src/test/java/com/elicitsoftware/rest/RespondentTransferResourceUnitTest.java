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

import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.rest.RespondentImportResource.ImportResponse;
import com.elicitsoftware.service.RespondentExportService;
import com.elicitsoftware.service.RespondentImportService;
import com.elicitsoftware.service.RespondentImportService.ImportResult;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-011: Export Respondent Data and UC-012: Import Respondent Data — how the two REST resources
 * turn the services' outcomes into HTTP responses, with the services stubbed.
 * <p>
 * Covers BR-042 (a rejected import reports why and imports nothing) and BR-100 (a respondent
 * that cannot be exported portably is a conflict, not a server error).
 */
class RespondentTransferResourceUnitTest {

    private static MultipartBody body(InputStream file) {
        MultipartBody body = new MultipartBody();
        body.file = file;
        return body;
    }

    private static RespondentImportResource importer(Function<InputStream, ImportResult> behaviour) {
        RespondentImportResource resource = new RespondentImportResource();
        resource.respondentImportService = new RespondentImportService() {
            @Override
            public ImportResult importFromFile(InputStream inputStream) {
                return behaviour.apply(inputStream);
            }
        };
        return resource;
    }

    private static RespondentExportResource exporter(Function<Integer, String> behaviour) {
        RespondentExportResource resource = new RespondentExportResource();
        resource.respondentExportService = new RespondentExportService() {
            @Override
            public String exportRespondent(Integer respondentId) {
                return behaviour.apply(respondentId);
            }
        };
        return resource;
    }

    @Test
    void given_noFile_when_imported_then_theRequestIsBad() {
        Response response = importer(in -> {
            throw new AssertionError("not called");
        }).importRespondent(new MultipartBody());
        assertEquals(400, response.getStatus());
        ImportResponse entity = (ImportResponse) response.getEntity();
        assertFalse(entity.isSuccess());
        assertEquals(0, entity.getRecordsImported());
        assertEquals("No file provided in the 'file' field", entity.getMessage());
        assertNull(entity.getCounts());
    }

    @Test
    void given_aSuccessfulImport_when_imported_then_itIsOkWithTheCounts() {
        InputStream file = new ByteArrayInputStream(new byte[0]);
        Map<String, Integer> counts = Map.of("respondents", 1, "answers", 4);
        InputStream[] received = new InputStream[1];
        Response response = importer(in -> {
            received[0] = in;
            return new ImportResult(true, 5, List.of(), counts);
        }).importRespondent(body(file));

        assertSame(file, received[0]);
        assertEquals(200, response.getStatus());
        ImportResponse entity = (ImportResponse) response.getEntity();
        assertTrue(entity.isSuccess());
        assertEquals(5, entity.getRecordsImported());
        assertEquals("Import completed successfully", entity.getMessage());
        assertEquals(counts, entity.getCounts());
    }

    @Test
    void given_anImportWithErrors_when_imported_then_theErrorsAreJoined() {
        Response response = importer(in -> new ImportResult(false, 0, List.of("Line 1: a", "Line 2: b"), Map.of()))
                .importRespondent(body(new ByteArrayInputStream(new byte[0])));
        assertEquals(400, response.getStatus());
        ImportResponse entity = (ImportResponse) response.getEntity();
        assertFalse(entity.isSuccess());
        assertEquals("Line 1: a; Line 2: b", entity.getMessage());
        assertEquals(Map.of(), entity.getCounts());
    }

    @Test
    void given_anUnresolvableReference_when_imported_then_theAdministratorReadsTheReason() {
        Response response = importer(in -> {
            throw new RespondentImportService.ImportValidationException("Line 2: No survey with survey_key x");
        }).importRespondent(body(new ByteArrayInputStream(new byte[0])));
        assertEquals(400, response.getStatus());
        ImportResponse entity = (ImportResponse) response.getEntity();
        assertFalse(entity.isSuccess());
        assertEquals("Line 2: No survey with survey_key x", entity.getMessage());
        assertNull(entity.getCounts());
    }

    @Test
    void given_anUnexpectedFailure_when_imported_then_onlyACorrelationIdIsReturned() {
        Response response = importer(in -> {
            throw new IllegalStateException("constraint fk_answers violated");
        }).importRespondent(body(new ByteArrayInputStream(new byte[0])));
        assertEquals(500, response.getStatus());
        ImportResponse entity = (ImportResponse) response.getEntity();
        assertFalse(entity.isSuccess());
        assertTrue(entity.getMessage().matches(
                "Import failed due to an unexpected error\\. Reference: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                entity.getMessage());
    }

    @Test
    void given_anImportResponse_when_itsSettersAreUsed_then_theGettersReturnTheValues() {
        ImportResponse response = new ImportResponse();
        response.setSuccess(true);
        response.setRecordsImported(7);
        response.setMessage("m");
        response.setCounts(Map.of("a", 1));
        assertTrue(response.isSuccess());
        assertEquals(7, response.getRecordsImported());
        assertEquals("m", response.getMessage());
        assertEquals(Map.of("a", 1), response.getCounts());
    }

    @Test
    void given_noRespondentId_when_exported_then_theRequestIsBad() {
        Response response = exporter(id -> {
            throw new AssertionError("not called");
        }).exportRespondent(null, null);
        assertEquals(400, response.getStatus());
        assertEquals("Missing required query parameter: id", response.getEntity());
        assertEquals("text/plain", response.getMediaType().toString());
    }

    @Test
    void given_aRespondentId_when_exported_then_theFileIsAnAttachmentNamedForIt() {
        Integer[] asked = new Integer[1];
        RespondentExportResource resource = exporter(id -> {
            asked[0] = id;
            return "# ELICIT_EXPORT_V2\né\n";
        });

        Response response = resource.exportRespondent(12, 99);
        assertEquals(12, asked[0], "id wins over respondent_id");
        assertEquals(200, response.getStatus());
        assertArrayEquals("# ELICIT_EXPORT_V2\né\n".getBytes(StandardCharsets.UTF_8), (byte[]) response.getEntity());
        assertEquals("attachment; filename=\"respondent_12_export.elicit\"", response.getHeaderString("Content-Disposition"));
        assertEquals("application/octet-stream", response.getMediaType().toString());

        resource.exportRespondent(null, 99);
        assertEquals(99, asked[0], "respondent_id is the alias");
    }

    @Test
    void given_anUnknownRespondent_when_exported_then_itIsNotFound() {
        Response response = exporter(id -> {
            throw new IllegalArgumentException("Respondent not found: " + id);
        }).exportRespondent(5, null);
        assertEquals(404, response.getStatus());
        assertEquals("Respondent not found: 5", response.getEntity());
        assertEquals("text/plain", response.getMediaType().toString());
    }

    @Test
    void given_aRespondentThatCannotBeExportedPortably_when_exported_then_itIsAConflict() {
        Response response = exporter(id -> {
            throw new IllegalStateException("Department 'Oncology' (id 4) has no code");
        }).exportRespondent(5, null);
        assertEquals(409, response.getStatus());
        assertEquals("Department 'Oncology' (id 4) has no code", response.getEntity());
        assertEquals("text/plain", response.getMediaType().toString());
    }
}
