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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-021: Warn About Default Accounts — the instruction the log and the console show while a
 * seeded account still exists (BR-085 the seeded usernames, BR-088 the instruction covers both
 * stores).
 */
class DefaultAccountInstructionTest {

    @Test
    void given_theSeededUsernames_when_listed_then_theyAreAdminAndUser() {
        assertEquals(List.of("admin", "user"), DefaultAccountCheck.SEEDED_USERNAMES);
    }

    @Test
    void given_noSeededAccount_when_theInstructionIsBuilt_then_itIsEmpty() {
        assertEquals("", DefaultAccountCheck.instruction(List.of()));
    }

    @Test
    void given_oneSeededAccount_when_theInstructionIsBuilt_then_itSpeaksOfIt() {
        assertEquals("The database migrations seeded the default account 'admin'. Rename it before this deployment goes"
                + " live: open Admin > Users and change the username, and when elicit.authorization.mode=DATABASE rename"
                + " the matching account in the identity provider to the same value.",
                DefaultAccountCheck.instruction(List.of("admin")));
    }

    @Test
    void given_bothSeededAccounts_when_theInstructionIsBuilt_then_itSpeaksOfThem() {
        assertEquals("The database migrations seeded the default accounts 'admin' and 'user'. Rename them before this"
                + " deployment goes live: open Admin > Users and change the username, and when"
                + " elicit.authorization.mode=DATABASE rename the matching account in the identity provider to the same value.",
                DefaultAccountCheck.instruction(List.of("admin", "user")));
    }
}
