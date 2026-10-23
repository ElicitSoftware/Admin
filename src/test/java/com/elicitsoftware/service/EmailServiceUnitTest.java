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
import com.elicitsoftware.model.Message;
import com.elicitsoftware.model.Status;
import com.elicitsoftware.model.Subject;
import io.quarkus.mailer.Mail;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-004: Send Invitation or Reminder Email, and UC-024: Diagnose Email — how {@link EmailService}
 * builds each message and reports the relay's answer, with the mailer stubbed (no SMTP).
 * <p>
 * Covers BR-093 (the test email uses the invitation mailer, sender and timeout) and BR-095 (the
 * test email is attributed to the administrator who asked).
 */
class EmailServiceUnitTest {

    private final List<Mail> sent = new ArrayList<>();

    private EmailService service(Supplier<Uni<Void>> answer) {
        EmailService service = new EmailService();
        service.mailer = mails -> {
            sent.addAll(List.of(mails));
            return answer.get();
        };
        service.fromEmail = "elicit@example.org";
        service.mailerHost = "smtp.example.org";
        service.mailerPort = 2525;
        service.mailSendTimeoutSeconds = 5;
        return service;
    }

    private EmailService accepting() {
        return service(() -> Uni.createFrom().voidItem());
    }

    private static boolean sendMessage(EmailService service, Message message) throws Exception {
        Method m = EmailService.class.getDeclaredMethod("sendMessage", Message.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, message);
    }

    private static Message message(String mimeType, String email) {
        Message message = new Message();
        message.id = 3L;
        message.mimeType = mimeType;
        message.subjectLine = "Your survey";
        message.body = "Please answer";
        if (email != null) {
            Subject subject = new Subject();
            subject.setEmail(email);
            message.subject = subject;
        }
        return message;
    }

    @Test
    void given_anAcceptingRelay_when_aTestEmailIsSent_then_itIsUpAndNamesTheRelay() {
        CheckResult result = accepting().sendTestEmail("jane.doe@example.org", "alice");

        assertEquals(CheckResult.Status.UP, result.status());
        assertEquals("test email to j*******@example.org", result.name());
        assertEquals("accepted by smtp.example.org:2525", result.detail());
        assertTrue(result.durationMs() >= 0);

        assertEquals(1, sent.size());
        Mail mail = sent.get(0);
        assertEquals(List.of("jane.doe@example.org"), mail.getTo());
        assertEquals("elicit@example.org", mail.getFrom());
        assertEquals("Elicit Admin test email", mail.getSubject());
        assertEquals("This is a test message from Elicit Admin, sent by alice to confirm that the mail relay at "
                + "smtp.example.org:2525 accepts mail from elicit@example.org. No action is needed.", mail.getText());
        assertNull(mail.getHtml());
    }

    @Test
    void given_noSenderAddress_when_aTestEmailIsRequested_then_nothingIsSentAndTheResultIsUnknown() {
        EmailService service = accepting();
        service.fromEmail = null;
        CheckResult missing = service.sendTestEmail("a@b.org", "alice");
        service.fromEmail = "  ";
        CheckResult blank = service.sendTestEmail("a@b.org", "alice");

        for (CheckResult result : List.of(missing, blank)) {
            assertEquals(CheckResult.Status.UNKNOWN, result.status());
            assertEquals("no sender address is configured (quarkus.mailer.from)", result.detail());
            assertEquals("test email to a*@b.org", result.name());
        }
        assertTrue(sent.isEmpty());
    }

    @Test
    void given_aRelayThatTimesOut_when_aTestEmailIsSent_then_itIsDownWithTheTimeout() {
        CheckResult result = service(() -> Uni.createFrom().failure(new TimeoutException())).sendTestEmail("a@b.org", "alice");
        assertEquals(CheckResult.Status.DOWN, result.status());
        assertEquals("no answer from smtp.example.org:2525 within 5 s", result.detail());
    }

    @Test
    void given_aRelayThatRefuses_when_aTestEmailIsSent_then_theReasonIsTheRootMessage() {
        CheckResult wrapped = service(() -> Uni.createFrom().failure(new RuntimeException("outer", new IOException("refused"))))
                .sendTestEmail("a@b.org", "alice");
        assertEquals(CheckResult.Status.DOWN, wrapped.status());
        assertEquals("smtp.example.org:2525 refused", wrapped.detail());

        CheckResult bare = service(() -> Uni.createFrom().failure(new IllegalStateException("closed")))
                .sendTestEmail("a@b.org", "alice");
        assertEquals("smtp.example.org:2525 closed", bare.detail());

        CheckResult silent = service(() -> {
            throw new IllegalStateException();
        }).sendTestEmail("a@b.org", "alice");
        assertEquals(CheckResult.Status.DOWN, silent.status());
        assertEquals("smtp.example.org:2525 IllegalStateException", silent.detail());
    }

    @Test
    void given_anHtmlMessage_when_sent_then_itGoesAsHtmlFromTheConfiguredSender() throws Exception {
        assertTrue(sendMessage(accepting(), message("text/html", "jane@example.org")));

        Mail mail = sent.get(0);
        assertEquals(List.of("jane@example.org"), mail.getTo());
        assertEquals("elicit@example.org", mail.getFrom());
        assertEquals("Your survey", mail.getSubject());
        assertEquals("Please answer", mail.getHtml());
        assertNull(mail.getText());
    }

    @Test
    void given_aPlainMessage_when_sent_then_itGoesAsText() throws Exception {
        assertTrue(sendMessage(accepting(), message("text/plain", "jane@example.org")));
        assertEquals("Please answer", sent.get(0).getText());
        assertNull(sent.get(0).getHtml());
    }

    @Test
    void given_aMessageWithoutRecipient_when_sent_then_itIsNotSent() throws Exception {
        assertFalse(sendMessage(accepting(), message("text/plain", null)));
        Message noEmail = message("text/plain", null);
        noEmail.subject = new Subject();
        assertFalse(sendMessage(accepting(), noEmail));
        assertTrue(sent.isEmpty());
    }

    @Test
    void given_aFailingRelay_when_aMessageIsSent_then_itIsReportedUnsent() throws Exception {
        assertFalse(sendMessage(service(() -> Uni.createFrom().failure(new TimeoutException())), message("text/plain", "j@x.org")));
        assertFalse(sendMessage(service(() -> Uni.createFrom().failure(new IllegalStateException("no"))), message("text/plain", "j@x.org")));
    }

    @Test
    void given_noDepartmentCanBeRead_when_anInvitationIsSent_then_itIsReportedUnsent() {
        Status status = new Status();
        status.setAccessCode("ABCDEFGH");
        status.setEmail("j@x.org");
        status.setDepartmentId(1);
        assertFalse(accepting().sendEmail(status));
        assertTrue(sent.isEmpty());
    }
}
