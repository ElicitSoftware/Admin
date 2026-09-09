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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link MessageType}: accessors and ID-based identity.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email) / UC-007 (Manage Message
 * Templates) — every {@link Message} and {@link MessageTemplate} is categorized by a
 * MessageType.</p>
 */
class MessageTypeTest {

    @Test
    void settersUpdateFields() {
        MessageType type = new MessageType();
        type.setId(3L);
        type.setName("Invitation");

        assertEquals(3L, type.getId());
        assertEquals("Invitation", type.getName());
    }

    @Test
    void messageTypesWithSameIdAreEqual() {
        MessageType a = new MessageType();
        a.setId(1L);
        MessageType b = new MessageType();
        b.setId(1L);
        b.setName("Different name, same id");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, "not a message type", "a MessageType is never equal to a different type");
    }

    @Test
    void messageTypesWithDifferentIdsAreNotEqual() {
        MessageType a = new MessageType();
        a.setId(1L);
        MessageType b = new MessageType();
        b.setId(2L);

        assertNotEquals(a, b);
    }

    @Test
    void deduplicatesInSetById() {
        MessageType a = new MessageType();
        a.setId(1L);
        MessageType sameId = new MessageType();
        sameId.setId(1L);
        MessageType other = new MessageType();
        other.setId(2L);

        Set<MessageType> set = Set.of(a, other);
        assertEquals(2, set.size());
        assertTrue(set.contains(sameId));
    }
}
