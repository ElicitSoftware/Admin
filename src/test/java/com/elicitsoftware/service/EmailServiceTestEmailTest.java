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

import com.elicitsoftware.diagnostics.CheckResult;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test email goes through the same sender and mailer as invitations.
 *
 * <p>Traceability: UC-024 (Diagnose Email), BR-093, BR-095.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class EmailServiceTestEmailTest {

    @Inject
    EmailService emailService;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    /** UC-024 steps 5 and 6 / BR-093: one plain-text message from the configured sender. */
    @Test
    void sendsOneMessageFromTheConfiguredSender() {
        CheckResult result = emailService.sendTestEmail("ops@example.org", "system.admin");

        assertTrue(result.isUp(), result.detail());
        List<Mail> sent = mailbox.getMailsSentTo("ops@example.org");
        assertEquals(1, sent.size());
        assertEquals("test@example.org", sent.get(0).getFrom());
        assertEquals("Elicit Admin test email", sent.get(0).getSubject());
        assertTrue(sent.get(0).getText().contains("system.admin"), "the body names who asked (BR-095)");
    }
}
