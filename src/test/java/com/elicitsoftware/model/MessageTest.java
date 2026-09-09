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

import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for {@link Message}: constructors, field access, and ID-based identity.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email). {@code Message} models a queued
 * outbound email; these are non-booted tests exercising in-memory state directly.
 * {@link Message#createMessagesForSubject} (the department-template-driven factory method) is
 * exercised against a real database via {@code CsvImportServiceTest}, so it is not duplicated
 * here.</p>
 */
class MessageTest {

    @Test
    void noArgConstructorDefaultsMimeTypeAndSetsCreatedDt() {
        Message message = new Message();
        assertEquals("text/html", message.mimeType);
        assertNotNull(message.createdDt);
        assertNull(message.sentDt);
    }

    @Test
    void coreFieldsConstructorSetsProvidedFields() {
        Subject subject = new Subject();
        MessageType type = new MessageType();

        Message message = new Message(subject, type, "Welcome", "Please complete your survey");

        assertEquals(subject, message.subject);
        assertEquals(type, message.messageType);
        assertEquals("Welcome", message.subjectLine);
        assertEquals("Please complete your survey", message.body);
        assertEquals("text/html", message.mimeType);
        assertNotNull(message.createdDt);
    }

    @Test
    void fieldsCanBeSetDirectly() {
        Message message = new Message();
        Date sentDt = new Date();

        message.id = 11L;
        message.mimeType = "text/plain";
        message.sentDt = sentDt;

        assertEquals(11L, message.id);
        assertEquals("text/plain", message.mimeType);
        assertEquals(sentDt, message.sentDt);
    }

    @Test
    void messagesWithSameIdAreEqual() {
        Message a = new Message();
        a.id = 1L;
        Message b = new Message();
        b.id = 1L;
        b.body = "different body, same id";

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a, "a Message is equal to itself");
        assertNotEquals(a, "not a message", "a Message is never equal to a different type");
    }

    @Test
    void messagesWithNullIdOnBothSidesAreEqual() {
        Message a = new Message();
        Message b = new Message();

        assertEquals(a, b, "two unpersisted messages both have a null id");
    }

    @Test
    void deduplicatesInSetById() {
        Message a = new Message();
        a.id = 1L;
        Message sameId = new Message();
        sameId.id = 1L;
        Message other = new Message();
        other.id = 2L;

        Set<Message> set = Set.of(a, other);
        assertEquals(2, set.size());
        assertTrue(set.contains(sameId));
        assertNotEquals(a, other);
    }
}
