package com.elicitsoftware.exception;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link AccessCodeGenerationError}.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email) — thrown by
 * {@code Message.createMessagesForSubject} when a department's message-template
 * configuration is invalid.</p>
 */
class AccessCodeGenerationErrorTest {

    @Test
    void constructorSetsMessage() {
        AccessCodeGenerationError error = new AccessCodeGenerationError("invalid departmentid");
        assertEquals("invalid departmentid", error.getMessage());
    }
}
