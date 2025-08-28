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

import com.elicitsoftware.exception.TokenGenerationError;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

import java.util.ArrayList;
import java.util.Date;

/**
 * JPA entity representing a message to be sent to a survey respondent.
 *
 * <p>The Message entity models outbound communications in the Elicit survey system.
 * Each message is associated with a subject (survey instance), a message type, and
 * contains content generated from a message template. Messages are queued for delivery
 * and processed by the email service. The {@code sentDt} field tracks when the message
 * was actually sent.</p>
 *
 * <p><strong>Key Fields:</strong></p>
 * <ul>
 *   <li><strong>subject:</strong> The survey subject (and respondent) this message is for</li>
 *   <li><strong>messageType:</strong> The type/category of message (e.g., invitation, reminder)</li>
 *   <li><strong>mimeType:</strong> Content type (e.g., text/html)</li>
 *   <li><strong>subjectLine:</strong> Email subject line</li>
 *   <li><strong>body:</strong> Email/message body (may contain tokens replaced at creation)</li>
 *   <li><strong>createdDt:</strong> When the message was created</li>
 *   <li><strong>sentDt:</strong> When the message was sent (null if unsent)</li>
 * </ul>
 *
 * <p><strong>Usage:</strong></p>
 * <ul>
 *   <li>Created when a new respondent is added or when a survey event triggers a message</li>
 *   <li>Processed and sent by the EmailService</li>
 *   <li>Supports token replacement (e.g., &amp;lt;TOKEN&amp;gt; for personalized links)</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @see Subject
 * @see MessageType
 * @see MessageTemplate
 * @see com.elicitsoftware.service.EmailService
 */
@Entity
@Table(name = "messages", schema = "survey")
public class Message extends PanacheEntityBase {

    /**
     * Unique identifier for the message.
     *
     * <p>Auto-generated primary key using a database sequence. This ID uniquely
     * identifies each message within the system and is used for tracking
     * message delivery status and audit purposes.</p>
     *
     * <p><strong>Generation Strategy:</strong></p>
     * <ul>
     *   <li><strong>Sequence Name:</strong> {@code survey.messages_seq}</li>
     *   <li><strong>Allocation Size:</strong> 1 (no batch allocation)</li>
     *   <li><strong>Database Type:</strong> BIGINT</li>
     * </ul>
     */
    @Id
    @SequenceGenerator(name = "MESSAGES_ID_GENERATOR", schema = "survey", sequenceName = "messages_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MESSAGES_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false)
    public Long id;

    /**
     * The survey subject (and associated respondent) this message is intended for.
     *
     * <p>Establishes the relationship between the message and the specific survey
     * instance. Through this relationship, the system can access respondent details
     * such as email address, name, and personalization tokens.</p>
     *
     * @see Subject
     */
    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    @NotNull(message = "Subject must not be null")
    public Subject subject;

    /**
     * The type/category of this message.
     *
     * <p>Defines the purpose and context of the message (e.g., invitation, reminder,
     * thank you, follow-up). This classification helps in message template selection,
     * delivery scheduling, and reporting.</p>
     *
     * @see MessageType
     */
    @ManyToOne
    @JoinColumn(name = "message_type", nullable = false)
    @NotNull(message = "Message type must not be null")
    public MessageType messageType;

    /**
     * MIME type for the message content.
     *
     * <p>Specifies the content type of the message body, typically "text/html" for
     * rich HTML emails or "text/plain" for plain text messages. This field controls
     * how the email client renders the message content.</p>
     *
     * <p><strong>Common Values:</strong></p>
     * <ul>
     *   <li>{@code text/html} - HTML formatted email (default)</li>
     *   <li>{@code text/plain} - Plain text email</li>
     * </ul>
     */
    @Column(name = "mime_type", nullable = false, length = 100)
    @NotNull(message = "Mime type must not be null")
    public String mimeType = "text/html";

    /**
     * Email subject line for the message.
     *
     * <p>The subject line that will appear in the recipient's email client.
     * This field is populated from the message template and may contain
     * personalized content based on the survey or respondent data.</p>
     *
     * <p><strong>Constraints:</strong></p>
     * <ul>
     *   <li><strong>Max Length:</strong> 255 characters</li>
     *   <li><strong>Required:</strong> Cannot be blank</li>
     * </ul>
     */
    @Column(name = "subjectline", nullable = false, length = 255)
    @NotBlank(message = "Subject cannot be blank")
    @Length(max = 255, message = "Subject max length 255 characters")
    public String subjectLine;

    /**
     * The main content/body of the message.
     *
     * <p>Contains the actual message content that will be sent to the respondent.
     * This content is generated from message templates with token replacement
     * (e.g., &lt;TOKEN&gt; replaced with actual survey URLs or personalization data).
     * Supports both HTML and plain text formats based on the {@link #mimeType}.</p>
     *
     * <p><strong>Constraints:</strong></p>
     * <ul>
     *   <li><strong>Max Length:</strong> 6000 characters</li>
     *   <li><strong>Required:</strong> Cannot be blank</li>
     * </ul>
     *
     * <p><strong>Token Replacement:</strong></p>
     * <ul>
     *   <li>{@code <TOKEN>} - Replaced with respondent's unique survey token</li>
     *   <li>Other tokens may be supported based on template configuration</li>
     * </ul>
     */
    @Column(name = "body", nullable = false, length = 6000)
    @NotBlank(message = "Body cannot be blank")
    @Length(max = 6000, message = "Body max length 50 characters")
    public String body;

    /**
     * Timestamp when the message was created.
     *
     * <p>Automatically set to the current timestamp when a new message instance
     * is created. This field is used for message queuing, audit trails, and
     * determining message age for cleanup or reporting purposes.</p>
     *
     * <p><strong>Default Value:</strong> Current timestamp at object creation</p>
     */
    @Column(name = "created_dt", nullable = false)
    public Date createdDt = new Date();

    /**
     * Timestamp when the message was successfully sent.
     *
     * <p>Initially null when the message is created. Set to the current timestamp
     * by the EmailService when the message is successfully delivered. Messages
     * with null {@code sentDt} are considered pending and will be processed by
     * the scheduled email delivery service.</p>
     *
     * <p><strong>States:</strong></p>
     * <ul>
     *   <li><strong>null:</strong> Message is queued/pending delivery</li>
     *   <li><strong>timestamp:</strong> Message was successfully sent at this time</li>
     * </ul>
     *
     * @see com.elicitsoftware.service.EmailService
     */
    @Column(name = "sent_dt")
    public Date sentDt;

    /**
     * Default constructor for JPA.
     *
     * <p>Creates a new Message instance with default values. The {@link #createdDt}
     * field is automatically set to the current timestamp.</p>
     */
    public Message() {
        super();
    }

    /**
     * Constructor for creating a new message with core required fields.
     *
     * <p>Creates a new Message instance with the specified subject, message type,
     * subject line, and body. The {@link #createdDt} field is automatically set
     * to the current timestamp, and {@link #mimeType} defaults to "text/html".</p>
     *
     * @param subject the survey subject this message is for
     * @param messageType the type/category of this message
     * @param subjectLine the email subject line
     * @param body the message content/body
     */
    public Message(Subject subject, MessageType messageType, String subjectLine, String body) {
        this.subject = subject;
        this.messageType = messageType;
        this.subjectLine = subjectLine;
        this.body = body;
        this.createdDt = new Date();
    }

    /**
     * Creates messages for a subject based on their department's default message templates.
     *
     * <p>This method generates one or more messages for a survey subject by processing
     * the default message templates configured for the subject's department. It handles
     * comma-separated template IDs, performs token replacement, and creates ready-to-send
     * message instances.</p>
     *
     * <p><strong>Process:</strong></p>
     * <ol>
     *   <li>Retrieves the subject's department and default message template IDs</li>
     *   <li>Splits comma-separated template IDs into individual templates</li>
     *   <li>For each template, creates a message with token replacement</li>
     *   <li>Replaces &lt;TOKEN&gt; placeholders with the respondent's actual token</li>
     *   <li>Returns a list of message instances ready for persistence</li>
     * </ol>
     *
     * <p><strong>Token Replacement:</strong></p>
     * <ul>
     *   <li>{@code <TOKEN>} is replaced with the respondent's unique survey token</li>
     *   <li>If the respondent's token is null, an empty string is used</li>
     * </ul>
     *
     * @param subject the survey subject to create messages for
     * @return ArrayList of Message instances created from department templates
     * @throws TokenGenerationError if the department is invalid or template IDs are malformed
     *
     * @see Department#defaultMessageId
     * @see MessageTemplate
     * @see Subject#getRespondent()
     */
    public static ArrayList<Message> createMessagesForSubject(Subject subject) throws TokenGenerationError {
        ArrayList<Message> messages = new ArrayList<>();
        Department department = Department.findById(subject.getDepartmentId());
        if (department == null || department.defaultMessageId == null) {
            throw  new TokenGenerationError("invalid departmentid");
        }

        // Split comma-separated message template IDs
        String[] templateIds = department.defaultMessageId.split(",");

        for (String templateIdStr : templateIds) {
            try {
                Long templateId = Long.parseLong(templateIdStr.trim());
                MessageTemplate template = MessageTemplate.findById(templateId);

                if (template != null) {
                    // Replace <TOKEN> in the message body with respondent's token
                    String processedMessage = template.message != null ?
                            template.message.replace("<TOKEN>", subject.getRespondent().token != null ? subject.getRespondent().token : "") : "";

                    // Create new message
                    Message message = new Message();
                    message.subject = subject;
                    message.messageType = template.messageType;
                    message.subjectLine = template.subject;
                    message.mimeType = template.mimeType;
                    message.body = processedMessage;
                    messages.add(message);
                }
            } catch (NumberFormatException e) {
                // Log error parsing template ID
                throw  new TokenGenerationError("Invalid message template ID: " + templateIdStr);
            }
        }
        return messages;
    }
}
