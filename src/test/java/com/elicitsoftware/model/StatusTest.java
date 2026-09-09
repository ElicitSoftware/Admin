package com.elicitsoftware.model;

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

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link Status} field accessors and display helpers.
 *
 * <p>Traceability: UC-002 (Search and Monitor Subject Progress). ID-based equality/hashCode is
 * already covered by {@code EntityIdentityTest}; these tests cover the remaining participant
 * fields surfaced in the console's status grid, plus the formatted-date helpers.</p>
 */
class StatusTest {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("MM/dd/yyyy");

    @Test
    void settersUpdateAllFields() {
        Status status = new Status();
        Date dob = new Date(0);
        Date createdDt = new Date();
        Date finalizedDt = new Date();

        status.setId(1L);
        status.setRespondentId(2L);
        status.setXid("XID-1");
        status.setSurveyId(3L);
        status.setFirstName("Pat");
        status.setLastName("Tester");
        status.setMiddleName("Q");
        status.setDob(dob);
        status.setEmail("pat@example.org");
        status.setPhone("555-123-4567");
        status.setDepartmentName("Cardiology");
        status.setDepartmentId(4L);
        status.setToken("tok-1");
        status.setStatus("In Progress");
        status.setCreatedDt(createdDt);
        status.setFinalizedDt(finalizedDt);

        assertEquals(1L, status.getId());
        assertEquals(2L, status.getRespondentId());
        assertEquals("XID-1", status.getXid());
        assertEquals(3L, status.getSurveyId());
        assertEquals("Pat", status.getFirstName());
        assertEquals("Tester", status.getLastName());
        assertEquals("Q", status.getMiddleName());
        assertEquals(dob, status.getDob());
        assertEquals("pat@example.org", status.getEmail());
        assertEquals("555-123-4567", status.getPhone());
        assertEquals("Cardiology", status.getDepartmentName());
        assertEquals(4L, status.getDepartmentId());
        assertEquals("tok-1", status.getToken());
        assertEquals("In Progress", status.getStatus());
        assertEquals(createdDt, status.getCreatedDt());
        assertEquals(finalizedDt, status.getFinalizedDt());
    }

    @Test
    void getCreatedFormatsCreatedDt() {
        Status status = new Status();
        Date createdDt = new Date(1_700_000_000_000L);
        status.setCreatedDt(createdDt);

        assertEquals(SDF.format(createdDt), status.getCreated());
    }

    @Test
    void toStringIncludesStatusTokenAndFormattedDate() {
        Status status = new Status();
        Date createdDt = new Date(1_700_000_000_000L);
        status.setStatus("Finished");
        status.setToken("tok-2");
        status.setCreatedDt(createdDt);

        String result = status.toString();

        assertTrue(result.contains("Finished"));
        assertTrue(result.contains("tok-2"));
        assertTrue(result.contains(SDF.format(createdDt)));
    }

    @Test
    void toStringHandlesNullFieldsGracefully() {
        Status status = new Status();
        status.setCreatedDt(null);

        assertEquals("null, null, null, ", status.toString());
    }
}
