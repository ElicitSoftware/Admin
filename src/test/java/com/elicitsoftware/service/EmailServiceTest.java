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
import com.elicitsoftware.test.UnreachableMailerTestProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Booted test for {@link EmailService#sendEmail(Status)} against a real (but unreachable) SMTP
 * endpoint.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email), alternative flow A2 (send
 * failure). Production logs showed the mail relay rejecting the connection while the "Send
 * Email" action in {@code SearchView} still reported success — because {@code sendEmail}
 * swallowed the per-template send exception and unconditionally returned {@code true}. This test
 * pins the fix: a real connection failure (not the mocked mailer, which always succeeds) must
 * make {@code sendEmail} return {@code false}.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(UnreachableMailerTestProfile.class)
class EmailServiceTest {

    @Inject
    EmailService emailService;

    /** UC-004/A2: a real SMTP connection failure must be reported as a failed send, not success. */
    @Test
    @TestTransaction
    void sendEmailReturnsFalseWhenMailServerIsUnreachable() {
        MessageType messageType = new MessageType();
        messageType.setName("UC004 email");
        messageType.persist();

        Department department = new Department();
        department.name = "UC004 Dept";
        department.code = "UC004";
        department.fromEmail = "uc004@example.org";
        department.persist();

        MessageTemplate template = new MessageTemplate();
        template.department = department;
        template.messageType = messageType;
        template.subject = "UC004 subject";
        template.message = "Hello <TOKEN>";
        template.mimeType = "text/plain";
        template.persist();

        department.defaultMessageId = String.valueOf(template.id);
        department.persist();

        Status status = new Status();
        status.setDepartmentId(department.id);
        status.setEmail("uc004-recipient@example.org");
        status.setToken("uc004-token");

        assertFalse(emailService.sendEmail(status),
                "sendEmail must return false when the mail server rejects the connection");
    }
}
