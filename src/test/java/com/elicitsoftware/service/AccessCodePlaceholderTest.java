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
import com.elicitsoftware.model.Message;
import com.elicitsoftware.model.MessageTemplate;
import com.elicitsoftware.model.MessageType;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Status;
import com.elicitsoftware.model.Subject;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invitation templates substitute the respondent's access code for {@code <ACCESS_CODE>}.
 *
 * <p>Traceability: UC-004 (Send Invitation or Reminder Email), BR-015. The placeholder used to be
 * {@code <TOKEN>}; it was renamed with no alias, so {@code <TOKEN>} must now pass through as
 * literal text. Covers both substitution sites: {@link Message#createMessagesForSubject}, which
 * builds the queued invitation, and {@link EmailService#sendEmail}, which sends a template
 * directly. Uses the test profile's mock mailer.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AccessCodePlaceholderTest {

    private static final String TEMPLATE = "Log in at http://localhost/#/login/<ACCESS_CODE> (old: <TOKEN>)";

    @Inject
    EmailService emailService;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    private Department persistDepartmentWithTemplate(String code, String mimeType) {
        MessageType messageType = new MessageType();
        messageType.setName("AC email " + code);
        messageType.persist();

        Department department = new Department();
        department.name = "AC Dept " + code;
        department.code = "AC-" + code;
        department.fromEmail = "access-code@example.org";
        department.persist();

        MessageTemplate template = new MessageTemplate();
        template.department = department;
        template.messageType = messageType;
        template.subject = "Your survey";
        template.message = TEMPLATE;
        template.mimeType = mimeType;
        template.persist();

        department.defaultMessageId = String.valueOf(template.id);
        department.persist();
        return department;
    }

    private Subject subjectWithAccessCode(Department department, String accessCode) {
        Survey survey = Survey.findById(1L);
        assertNotNull(survey, "test bootstrap should have seeded survey id=1");

        Respondent respondent = new Respondent();
        respondent.survey = survey;
        respondent.accessCode = accessCode;
        respondent.active = true;

        Subject subject = new Subject("XID-" + department.code, survey.id.longValue(), department.id,
                "Ada", "Code", null, LocalDate.of(1990, 1, 15), "ada.code@example.org", null);
        subject.setRespondent(respondent);
        return subject;
    }

    /** UC-004 BR-015: a queued invitation carries the respondent's access code. */
    @Test
    @TestTransaction
    void createMessagesForSubjectSubstitutesAccessCode() {
        Department department = persistDepartmentWithTemplate("MSG", "text/html");

        List<Message> messages = Message.createMessagesForSubject(subjectWithAccessCode(department, "Bx7kQ2mNp"));

        assertEquals(1, messages.size());
        assertEquals("Log in at http://localhost/#/login/Bx7kQ2mNp (old: <TOKEN>)", messages.get(0).body);
    }

    /** UC-004 BR-015: a respondent without an access code yields an empty substitution. */
    @Test
    @TestTransaction
    void createMessagesForSubjectTreatsMissingAccessCodeAsEmpty() {
        Department department = persistDepartmentWithTemplate("MSGNULL", "text/html");

        List<Message> messages = Message.createMessagesForSubject(subjectWithAccessCode(department, null));

        assertEquals("Log in at http://localhost/#/login/ (old: <TOKEN>)", messages.get(0).body);
    }

    /** UC-004 BR-015: a sent email carries the respondent's access code. */
    @Test
    @TestTransaction
    void sendEmailSubstitutesAccessCode() {
        Department department = persistDepartmentWithTemplate("MAIL", "text/plain");
        Status status = new Status();
        status.setDepartmentId(department.id);
        status.setEmail("ac-mail@example.org");
        status.setAccessCode("Rt5wZ9kLm");

        assertTrue(emailService.sendEmail(status));

        List<Mail> sent = mailbox.getMailsSentTo("ac-mail@example.org");
        assertEquals(1, sent.size());
        assertEquals("Log in at http://localhost/#/login/Rt5wZ9kLm (old: <TOKEN>)", sent.get(0).getText());
    }

    /** UC-004 BR-015: sending to a status with no access code must not fail. */
    @Test
    @TestTransaction
    void sendEmailTreatsMissingAccessCodeAsEmpty() {
        Department department = persistDepartmentWithTemplate("MAILNULL", "text/plain");
        Status status = new Status();
        status.setDepartmentId(department.id);
        status.setEmail("ac-mail-null@example.org");

        assertTrue(emailService.sendEmail(status));

        List<Mail> sent = mailbox.getMailsSentTo("ac-mail-null@example.org");
        assertEquals(1, sent.size());
        assertEquals("Log in at http://localhost/#/login/ (old: <TOKEN>)", sent.get(0).getText());
    }
}
