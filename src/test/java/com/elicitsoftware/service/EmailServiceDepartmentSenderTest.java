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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.MessageTemplate;
import com.elicitsoftware.model.MessageType;
import com.elicitsoftware.model.Status;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted test for the sender of an invitation sent from the console.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email), step 4 and BR-180: the message
 * comes from the department's From email, not the site's {@code quarkus.mailer.from}.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class EmailServiceDepartmentSenderTest {

    @Inject
    EmailService emailService;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    /** UC-004 step 4 / BR-180: the invitation is sent from the subject's department. */
    @Test
    @TestTransaction
    void invitationComesFromTheDepartmentsFromEmail() {
        MessageType messageType = new MessageType();
        messageType.setName("BR180 email");
        messageType.persist();

        Department department = new Department();
        department.name = "BR180 Dept";
        department.code = "BR180";
        department.fromEmail = "clinic@br180.example.org";
        department.persist();

        MessageTemplate template = new MessageTemplate();
        template.department = department;
        template.messageType = messageType;
        template.subject = "BR180 subject";
        template.message = "Hello <ACCESS_CODE>";
        template.mimeType = "text/plain";
        template.persist();

        department.defaultMessageId = String.valueOf(template.id);
        department.persist();

        Status status = new Status();
        status.setDepartmentId(department.id);
        status.setEmail("br180-recipient@example.org");
        status.setAccessCode("br180-code");

        assertTrue(emailService.sendEmail(status));
        List<Mail> sent = mailbox.getMailsSentTo("br180-recipient@example.org");
        assertEquals(1, sent.size());
        assertEquals("clinic@br180.example.org", sent.get(0).getFrom());
        assertEquals("Hello br180-code", sent.get(0).getText());
    }
}
