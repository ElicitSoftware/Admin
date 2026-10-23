package com.elicitsoftware.diagnostics;

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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-020: View the System Overview, step 5 — the seeded-account warning names every default
 * account still present, in one sentence that agrees in number with them.
 * <p>
 * The other warnings count departments and message templates through Panache, which needs a
 * running persistence unit, and are left to the booted tests.
 */
class SetupWarningsInstructionTest {

    @Test
    void given_oneAccount_when_instruction_then_singularAndQuoted() {
        String text = SetupWarnings.instruction(List.of("admin"));

        assertTrue(text.startsWith("The database migrations seeded the default account 'admin'. Rename it "), text);
    }

    @Test
    void given_twoAccounts_when_instruction_then_pluralAndJoinedWithAnd() {
        String text = SetupWarnings.instruction(List.of("admin", "user"));

        assertTrue(text.startsWith("The database migrations seeded the default accounts 'admin' and 'user'. Rename them "), text);
    }

    @Test
    void given_threeAccounts_when_instruction_then_eachIsNamed() {
        String text = SetupWarnings.instruction(List.of("a", "b", "c"));

        assertTrue(text.startsWith("The database migrations seeded the default accounts 'a' and 'b' and 'c'."), text);
    }
}
