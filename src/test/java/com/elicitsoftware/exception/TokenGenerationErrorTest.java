package com.elicitsoftware.exception;

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
 * Unit test for {@link TokenGenerationError}.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email) — thrown by
 * {@code Message.createMessagesForSubject} when a department's message-template
 * configuration is invalid.</p>
 */
class TokenGenerationErrorTest {

    @Test
    void constructorSetsMessage() {
        TokenGenerationError error = new TokenGenerationError("invalid departmentid");
        assertEquals("invalid departmentid", error.getMessage());
    }
}
