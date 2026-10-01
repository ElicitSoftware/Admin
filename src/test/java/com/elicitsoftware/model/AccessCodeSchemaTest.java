package com.elicitsoftware.model;

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

import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.response.AddResponseStatus;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The respondent credential is exposed as {@code access_code} / {@code accessCode} everywhere
 * Admin owns its shape.
 *
 * <p>Traceability: UC-002 (Search Subjects) for the {@code survey.status} view, UC-004 (Send
 * Invitation or Reminder Email) BR-015 for stored message templates, and UC-010 (Register
 * Subjects via Integration API) for the JSON returned by {@code /api/secured/add/*}.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AccessCodeSchemaTest {

    @Inject
    EntityManager em;

    @Inject
    Jsonb jsonb;

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    /** UC-002: the status view exposes access_code (V0.0.1/V0.0.7 on a fresh database). */
    @Test
    void statusViewExposesAccessCodeColumn() {
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = 'survey' AND table_name = 'status' AND column_name = 'access_code'"));
        assertEquals(0, count("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = 'survey' AND table_name = 'status' AND column_name = 'token'"));
    }

    /**
     * UC-004 BR-015: V0.0.18 converts stored templates, so no template carries the legacy
     * placeholder. (No template is seeded any more -- UC-028 C-016 -- so the conversion is
     * only observable as the absence of the old placeholder.)
     */
    @Test
    void storedTemplatesUseAccessCodePlaceholder() {
        assertEquals(0, count("SELECT COUNT(*) FROM survey.message_templates "
                + "WHERE message LIKE '%<TOKEN>%' OR subject LIKE '%<TOKEN>%'"));
    }

    /** UC-010: the registration response names the respondent credential accessCode. */
    @Test
    void registrationResponseSerializesAccessCode() {
        Status status = new Status();
        status.setAccessCode("Bx7kQ2mNp");
        AddResponse response = new AddResponse();
        response.addStatus(new AddResponseStatus(status, "New Subject"));

        String json = jsonb.toJson(response);

        assertTrue(json.contains("\"accessCode\":\"Bx7kQ2mNp\""), json);
        assertFalse(json.contains("\"token\""), json);
    }
}
