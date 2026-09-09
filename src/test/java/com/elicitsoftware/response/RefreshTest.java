package com.elicitsoftware.response;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link Refresh}: constructor field assignment.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email) — a per-respondent
 * refresh/messaging-status snapshot.</p>
 */
class RefreshTest {

    @Test
    void constructorSetsAllFields() {
        Refresh refresh = new Refresh("XID-1", 5L, "sent");

        assertEquals("XID-1", refresh.xid);
        assertEquals(5L, refresh.respondentId);
        assertEquals("sent", refresh.messageStatus);
    }
}
