package com.elicitsoftware.service;

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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Message;
import com.elicitsoftware.model.MessageTemplate;
import com.elicitsoftware.model.Status;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Date;
import java.util.List;

/**
 * Service for sending email messages to survey respondents.
 *
 * <p>This service handles the processing and delivery of email messages
 * to survey participants. It manages message queuing, template processing,
 * and actual email delivery through the configured email provider.</p>
 *
 * @author Elicit Software
 * @version 1.0
 * @see Message
 */
@ApplicationScoped
public class EmailService {

    /**
     * Reactive mailer instance for asynchronous email sending.
     * <p>
     * This mailer provides non-blocking email delivery capabilities
     * and integrates with Quarkus's reactive programming model.
     */
    @Inject
    ReactiveMailer mailer;

    /**
     * Configured sender email address for outgoing messages.
     * <p>
     * This address is used as the "From" field in all outgoing emails.
     * It should be configured via the quarkus.mailer.from property.
     */
    @ConfigProperty(name = "quarkus.mailer.from")
    String fromEmail;

    /**
     * Sends an immediate email notification for a participant status update.
     * <p>
     * This method provides immediate email delivery for status notifications,
     * typically used for urgent participant communications or system alerts.
     * The method uses a simple text format with hardcoded content suitable
     * for basic status notifications.
     * <p>
     * Email content:
     * - Subject: "Ahoy from Quarkus"
     * - Body: Simple notification message
     * - Format: Plain text
     * - Recipient: Extracted from status email field
     * <p>
     * Delivery process:
     * 1. Extract recipient email from status object
     * 2. Create plain text email with standard content
     * 3. Set configured sender address
     * 4. Send via reactive mailer with indefinite await
     * 5. Return success/failure status
     * <p>
     * Error handling:
     * - Catches all exceptions during email sending
     * - Logs success and failure messages to console
     * - Returns boolean indicating delivery success
     * - Does not throw exceptions (fail-safe behavior)
     *
     * @param status The participant status containing recipient email and notification context
     * @return true if email was sent successfully, false if sending failed
     * @see Status#getEmail()
     * @see Status#getToken()
     */

    /**
     * Default constructor for EmailService.
     * <p>
     * Creates a new EmailService instance with default values.
     * This constructor is used by frameworks and for general instantiation.
     */
    public EmailService() {
        // Default constructor
    }

    /**
     * Sends email notifications for the specified status.
     *
     * @param status the Status object containing email details
     * @return true if email was sent successfully, false otherwise
     */
    public boolean sendEmail(Status status) {
        Log.debugf("sendEmail: starting for token=%s, email=%s, departmentId=%s",
                status.getToken(), status.getEmail(), status.getDepartmentId());

        try {
            Department department = Department.findById(status.getDepartmentId());
            if (department == null) {
                Log.warnf("sendEmail: no department found for departmentId=%s, token=%s",
                        status.getDepartmentId(), status.getToken());
                return false;
            }
            Log.debugf("sendEmail: resolved department id=%d, defaultMessageId=%s",
                    department.id, department.defaultMessageId);

            String[] defaultMessagesIds = department.defaultMessageId.split(",");
            Log.debugf("sendEmail: %d message template(s) to send for token=%s",
                    defaultMessagesIds.length, status.getToken());

            boolean allSent = true;
            for (String defaultMessageID : defaultMessagesIds) {
                try {
                    Log.debugf("sendEmail: loading message template id=%s", defaultMessageID);
                    MessageTemplate messageTemplate = MessageTemplate.findById(Long.parseLong(defaultMessageID));
                    if (messageTemplate == null) {
                        Log.warnf("sendEmail: no message template found for id=%s, token=%s",
                                defaultMessageID, status.getToken());
                        continue;
                    }
                    String subject = messageTemplate.subject;
                    String body = messageTemplate.message.replace("<TOKEN>", status.getToken());
                    Log.debugf("sendEmail: template id=%s mimeType=%s subject='%s' bodyLength=%d",
                            messageTemplate.id, messageTemplate.mimeType, subject, body.length());

                    Log.debugf("sendEmail: dispatching to mailer, from=%s, to=%s", fromEmail, status.getEmail());
                    if (messageTemplate.mimeType.equals("text/html")) {
                        mailer.send(Mail.withHtml(status.getEmail(), subject, body).setFrom(fromEmail)).await().indefinitely();
                    } else {
                        mailer.send(Mail.withText(status.getEmail(), subject, body).setFrom(fromEmail)).await().indefinitely();
                    }
                    Log.debugf("sendEmail: mailer.send() returned for template id=%s, token=%s",
                            messageTemplate.id, status.getToken());
                } catch (Exception e) {
                    allSent = false;
                    Log.errorf(e, "sendEmail: failed to send template id=%s for token=%s",
                            defaultMessageID, status.getToken());
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof io.vertx.ext.mail.SMTPException) {
                        Log.errorf("sendEmail: SMTP rejected template id=%s for token=%s: %s",
                                defaultMessageID, status.getToken(), cause.getMessage());
                    }
                }
                Log.debug("sendEmail: template send attempt completed");
            }
            Log.debugf("sendEmail: finished for token=%s, allSent=%b", status.getToken(), allSent);
            return allSent;
        } catch (Exception ex) {
            Log.errorf(ex, "sendEmail: failed to send email for token=%s", status.getToken());
            return false;
        }
    }

    /**
     * Processes queued unsent messages via scheduled batch processing.
     * <p>
     * This method runs automatically every 5 minutes to process messages that
     * have been queued for delivery but not yet sent. It provides reliable
     * message delivery with automatic retry capabilities for the email system.
     * <p>
     * Processing workflow:
     * 1. **Query unsent messages**: Retrieve up to 100 messages where sentDt is null
     * 2. **Iterate through messages**: Process each message individually
     * 3. **Attempt delivery**: Use sendMessage() for actual email sending
     * 4. **Update database**: Mark successfully sent messages with timestamp
     * 5. **Error handling**: Log failures but continue processing remaining messages
     * <p>
     * Batch processing features:
     * - **Limited batch size**: Processes maximum 100 messages per execution
     * - **Performance optimization**: Prevents system overload during high volume
     * - **Transactional safety**: Database updates wrapped in transaction
     * - **Failure isolation**: Individual message failures don't stop batch processing
     * - **Comprehensive logging**: Detailed console output for monitoring
     * <p>
     * Database operations:
     * - Queries Message.find("sentDt is null") for unsent messages
     * - Updates message.sentDt with current timestamp upon successful delivery
     * - Persists changes immediately after each successful send
     * - Maintains data consistency through transactional processing
     * <p>
     * Scheduling configuration:
     * - Runs every 5 minutes via @Scheduled annotation
     * - Can be manually triggered for immediate processing
     * - Execution time depends on message volume and email server performance
     * - Failed executions are logged but don't affect subsequent runs
     * <p>
     * Error scenarios:
     * - **Database query failures**: Logged and method exits gracefully
     * - **Individual message failures**: Logged but processing continues
     * - **Email server issues**: Handled by sendMessage() method
     * - **Transaction failures**: Rolled back automatically by container
     * <p>
     * Monitoring and logging:
     * - Logs total number of messages found for processing
     * - Reports success/failure for each individual message
     * - Provides error details for troubleshooting
     * - Console output suitable for log aggregation systems
     *
     * @see #sendMessage(Message)
     * @see Message
     * @see Scheduled
     */
    @Scheduled(every = "5m")
    @Transactional
    public void processUnsentMessages() {
        Log.debug("processUnsentMessages: scheduled run starting");

        try {
            // Get up to 100 unsent messages
            List<Message> unsentMessages = Message.find("sentDt is null")
                    .page(0, 100)
                    .list();

            Log.debugf("processUnsentMessages: found %d unsent message(s)", unsentMessages.size());

            for (Message message : unsentMessages) {
                Log.debugf("processUnsentMessages: processing message id=%d, messageType=%s, mimeType=%s",
                        message.id, message.messageType, message.mimeType);
                if (sendMessage(message)) {
                    // Mark as sent
                    message.sentDt = new Date();
                    message.persist();
                    Log.debugf("processUnsentMessages: message id=%d sent successfully at %s",
                            message.id, message.sentDt);
                } else {
                    Log.warnf("processUnsentMessages: failed to send message id=%d", message.id);
                }
            }
            Log.debugf("processUnsentMessages: scheduled run complete, processed %d message(s)",
                    unsentMessages.size());
        } catch (Exception e) {
            Log.errorf(e, "processUnsentMessages: error processing unsent messages");
        }
    }

    /**
     * Sends an individual message with dynamic content type support and comprehensive validation.
     * <p>
     * This private method handles the actual email delivery for queued messages,
     * supporting both plain text and HTML content types. It performs thorough
     * validation before attempting delivery and provides detailed error handling
     * for troubleshooting failed sends.
     * <p>
     * Content type handling:
     * - **HTML emails**: When mimeType is "text/html", uses Mail.withHtml()
     * - **Plain text emails**: Default format for all other mime types
     * - **Dynamic selection**: Content type determined at runtime per message
     * - **Consistent formatting**: Same sender and recipient handling for both types
     * <p>
     * Validation process:
     * 1. **Message validation**: Ensures message object is not null
     * 2. **Subject validation**: Verifies message.subject is populated
     * 3. **Recipient validation**: Confirms subject.getEmail() returns valid email
     * 4. **Content validation**: Implicit validation of subject line and body
     * <p>
     * Email construction:
     * - **Recipient**: Extracted from message.subject.getEmail()
     * - **Subject line**: Uses message.subjectLine for email subject
     * - **Body content**: Uses message.body for email content
     * - **Sender**: Uses configured fromEmail address
     * - **Content type**: Determined by message.mimeType field
     * <p>
     * Delivery process:
     * 1. Validate message and recipient information
     * 2. Create appropriate Mail object based on content type
     * 3. Set sender address from configuration
     * 4. Send via reactive mailer with indefinite await
     * 5. Return success status for database update
     * <p>
     * Error handling:
     * - **Validation failures**: Log specific validation errors and return false
     * - **Delivery failures**: Catch exceptions, log details, return false
     * - **Null pointer protection**: Defensive coding for missing data
     * - **Exception isolation**: Failures don't propagate to calling methods
     * <p>
     * Performance considerations:
     * - **Blocking operation**: Uses await().indefinitely() for delivery confirmation
     * - **Individual processing**: Each message processed separately for isolation
     * - **Error recovery**: Failed messages can be retried in subsequent runs
     * - **Resource management**: Relies on mailer's connection pooling
     *
     * @param message The message object containing recipient, content, and formatting information
     * @return true if message was sent successfully, false if validation or sending failed
     * @see Message#subject
     * @see Message#subjectLine
     * @see Message#body
     * @see Message#mimeType
     */
    private boolean sendMessage(Message message) {
        Log.debugf("sendMessage: validating message id=%d", message.id);
        try {
            // Validate required fields
            if (message.subject == null || message.subject.getEmail() == null) {
                Log.warnf("sendMessage: message id=%d has no valid recipient email", message.id);
                return false;
            }

            Log.debugf("sendMessage: message id=%d to=%s subject='%s' mimeType=%s bodyLength=%d",
                    message.id, message.subject.getEmail(), message.subjectLine, message.mimeType,
                    message.body == null ? 0 : message.body.length());

            Mail mail;
            if ("text/html".equals(message.mimeType)) {
                mail = Mail.withHtml(
                        message.subject.getEmail(),
                        message.subjectLine,
                        message.body
                ).setFrom(fromEmail);
            } else {
                mail = Mail.withText(
                        message.subject.getEmail(),
                        message.subjectLine,
                        message.body
                ).setFrom(fromEmail);
            }

            Log.debugf("sendMessage: dispatching message id=%d to mailer, from=%s", message.id, fromEmail);
            mailer.send(mail).await().indefinitely();
            Log.debugf("sendMessage: mailer.send() returned for message id=%d", message.id);
            return true;
        } catch (Exception ex) {
            Log.errorf(ex, "sendMessage: failed to send message id=%d", message.id);
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof io.vertx.ext.mail.SMTPException) {
                Log.errorf("sendMessage: SMTP rejected message id=%d: %s", message.id, cause.getMessage());
            }
            return false;
        }
    }
}
