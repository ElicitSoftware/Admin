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

import com.elicitsoftware.test.ScriptedEntityManager;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-014: Import Survey Definition, UC-017: Update Survey Definition and UC-018: Apply Survey
 * Definition — the audit row written for every definition change and the latest applied revision
 * read back for the regression check, against a scripted entity manager.
 */
class SurveyLogServiceUnitTest {

    private static final UUID KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime REVISION = OffsetDateTime.parse("2026-05-01T10:00:00+02:00");

    private ScriptedEntityManager db;

    private SurveyLogService service(Object revisionResult) {
        db = new ScriptedEntityManager(call -> {
            if (call.is("nextval('survey.survey_log_seq')")) {
                return 77L;
            }
            if (call.is("MAX(revision)")) {
                return revisionResult;
            }
            return null;
        });
        SurveyLogService service = new SurveyLogService();
        service.em = db.entityManager();
        service.identity = QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("alice")).build();
        return service;
    }

    @Test
    void given_aSuccessfulChange_when_logged_then_theRowRecordsTheSummaryAndWhoDidIt() {
        service(null).logSuccess(3L, KEY, "IMPORT", "fhhs.elicit", "Installed", REVISION);

        ScriptedEntityManager.Call row = db.calls("INSERT INTO survey.survey_log").get(0);
        assertEquals(77L, row.param(1));
        assertEquals(3L, row.param(2));
        assertEquals(KEY, row.param(3));
        assertEquals("IMPORT", row.param(4));
        assertEquals("SUCCESS", row.param(5));
        assertEquals("alice", row.param(6));
        assertEquals("fhhs.elicit", row.param(7));
        assertEquals("Installed", row.param(8));
        assertNull(row.param(9));
        assertEquals(REVISION, row.param(10));
        assertTrue(row.params().containsKey(9));
    }

    @Test
    void given_aFailedChange_when_logged_then_theRowRecordsTheError() {
        service(null).logFailure(null, KEY, "UPDATE", "f.elicit", "Line 3: bad", null);

        ScriptedEntityManager.Call row = db.calls("INSERT INTO survey.survey_log").get(0);
        assertEquals("FAILURE", row.param(5));
        assertNull(row.param(8));
        assertEquals("Line 3: bad", row.param(9));
        assertNull(row.param(2));
        assertTrue(row.params().containsKey(8));
    }

    @Test
    void given_noSurveyKey_when_theLatestRevisionIsAsked_then_thereIsNoneAndNothingIsQueried() {
        assertNull(service(Instant.EPOCH).findLatestAppliedRevision(null));
        assertTrue(db.calls().isEmpty());
    }

    @Test
    void given_eachColumnType_when_theLatestRevisionIsRead_then_itIsNormalisedToUtc() {
        OffsetDateTime utc = REVISION.withOffsetSameInstant(ZoneOffset.UTC);
        assertEquals(utc, service(REVISION.toInstant()).findLatestAppliedRevision(KEY));
        assertEquals(KEY, db.calls().get(0).param(1));
        assertEquals(utc, service(REVISION).findLatestAppliedRevision(KEY));
        assertEquals(utc, service(Timestamp.from(REVISION.toInstant())).findLatestAppliedRevision(KEY));
        assertNull(service(null).findLatestAppliedRevision(KEY));
    }

    @Test
    void given_anUnexpectedColumnType_when_theLatestRevisionIsRead_then_itFailsLoudly() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service("2026").findLatestAppliedRevision(KEY));
        assertEquals("Unexpected revision column type: java.lang.String", e.getMessage());
    }

    @Test
    void given_anUnrecognisedRole_when_assigned_then_itIsRefusedBeforeAnythingIsWritten() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new UserRoleService().setRole(1L, "root"));
        assertEquals("Unrecognized role: root", e.getMessage());
    }
}
