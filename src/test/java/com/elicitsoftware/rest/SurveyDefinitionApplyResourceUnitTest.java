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
import com.elicitsoftware.service.SurveyDefinitionApplyService;
import com.elicitsoftware.service.SurveyDefinitionApplyService.ApplyResult;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-018: Apply Survey Definition — how {@link SurveyDefinitionApplyResource} hands the uploaded
 * bytes to the apply service and maps its result to an HTTP answer. The apply service is a stub;
 * nothing is booted.
 */
class SurveyDefinitionApplyResourceUnitTest {

    SurveyDefinitionApplyResource resource;
    ApplyResult result;
    RuntimeException failure;
    final List<Object> applied = new ArrayList<>();

    @BeforeEach
    void setUp() {
        resource = new SurveyDefinitionApplyResource();
        resource.applyService = new SurveyDefinitionApplyService() {
            @Override
            public ApplyResult apply(byte[] data, String fileName) {
                applied.add(data);
                applied.add(fileName);
                if (failure != null) {
                    throw failure;
                }
                return result;
            }
        };
    }

    private static MultipartBody body(InputStream file) {
        MultipartBody body = new MultipartBody();
        body.file = file;
        body.fileName = "f.elicit";
        return body;
    }

    private static SurveyDefinitionApplyResource.ApplyResponse entity(Response response) {
        return (SurveyDefinitionApplyResource.ApplyResponse) response.getEntity();
    }

    @Test
    void given_noFile_when_applied_then_badRequest() {
        Response response = resource.applySurvey(body(null));
        assertEquals(400, response.getStatus());
        assertEquals(new SurveyDefinitionApplyResource.ApplyResponse(false, null, "No file provided in the 'file' field",
                null, null), entity(response));
        assertTrue(applied.isEmpty());
    }

    @Test
    void given_aSuccessfulApply_when_answered_then_okNamingTheActionTaken() {
        UUID key = UUID.randomUUID();
        result = new ApplyResult(ApplyResult.Action.UPDATE, key, true, "Applied", "detail", "rebuilt");
        Response response = resource.applySurvey(body(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));

        assertEquals(200, response.getStatus());
        assertEquals(new SurveyDefinitionApplyResource.ApplyResponse(true, "UPDATE", "Applied", "detail", "rebuilt"),
                entity(response));
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), (byte[]) applied.get(0));
        assertEquals("f.elicit", applied.get(1));
    }

    @Test
    void given_aRejectedFile_when_answered_then_badRequestNamingTheRejection() {
        result = new ApplyResult(ApplyResult.Action.REJECTED, null, false, "no key", null);
        Response response = resource.applySurvey(body(new ByteArrayInputStream(new byte[0])));
        assertEquals(400, response.getStatus());
        assertEquals(new SurveyDefinitionApplyResource.ApplyResponse(false, "REJECTED", "no key", null, null),
                entity(response));
    }

    @Test
    void given_anUnreadableUpload_when_applied_then_badRequestAndNothingIsApplied() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("reset");
            }
        };
        Response response = resource.applySurvey(body(broken));
        assertEquals(400, response.getStatus());
        assertEquals("Failed to read the uploaded file", entity(response).message());
        assertNull(entity(response).action());
        assertTrue(applied.isEmpty());
    }

    @Test
    void given_anUnexpectedFailure_when_applied_then_aServerErrorWithAReference() {
        failure = new IllegalStateException("boom");
        Response response = resource.applySurvey(body(new ByteArrayInputStream(new byte[0])));
        assertEquals(500, response.getStatus());
        String prefix = "Apply failed due to an unexpected error. Reference: ";
        assertTrue(entity(response).message().startsWith(prefix), entity(response).message());
        UUID.fromString(entity(response).message().substring(prefix.length()));
        assertFalse(entity(response).success());
    }
}
