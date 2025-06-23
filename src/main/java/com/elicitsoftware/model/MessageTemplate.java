package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

/**
 * JPA entity representing message templates for survey communications in the Elicit system.
 * 
 * <p>This entity stores reusable message templates that can be used for various types of
 * survey-related communications such as invitations, reminders, and follow-up messages.
 * Templates are organized by department and message type to provide contextually appropriate
 * messaging for different scenarios.</p>
 * 
 * <p><strong>Template Features:</strong></p>
 * <ul>
 *   <li><strong>Department-Specific:</strong> Templates can be customized per department</li>
 *   <li><strong>Type Classification:</strong> Different templates for different message types</li>
 *   <li><strong>Rich Content:</strong> Support for various MIME types including HTML and plain text</li>
 *   <li><strong>Validation:</strong> Built-in validation for required fields and length constraints</li>
 * </ul>
 * 
 * <p><strong>Common Use Cases:</strong></p>
 * <ul>
 *   <li><strong>Survey Invitations:</strong> Initial invitations to participate in surveys</li>
 *   <li><strong>Reminders:</strong> Follow-up messages for incomplete surveys</li>
 *   <li><strong>Thank You Messages:</strong> Confirmation messages after survey completion</li>
 *   <li><strong>Notifications:</strong> Administrative notifications to survey participants</li>
 * </ul>
 * 
 * <p><strong>Template Structure:</strong></p>
 * <ul>
 *   <li><strong>Subject Line:</strong> Email subject with up to 255 characters</li>
 *   <li><strong>Message Body:</strong> Main content with up to 6000 characters</li>
 *   <li><strong>MIME Type:</strong> Content type specification (text/plain, text/html, etc.)</li>
 *   <li><strong>Department Context:</strong> Associated department for organizational relevance</li>
 *   <li><strong>Message Type:</strong> Classification for template purpose</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new message template
 * MessageTemplate template = new MessageTemplate();
 * template.department = Department.findById(1L);
 * template.messageType = MessageType.findById(1L);
 * template.subject = "Survey Invitation - Patient Experience";
 * template.message = "Dear [NAME], we invite you to participate in our survey...";
 * template.mimeType = "text/html";
 * template.persist();
 * 
 * // Find templates by department
 * List<MessageTemplate> deptTemplates = MessageTemplate.find("department", department).list();
 * 
 * // Find templates by type
 * List<MessageTemplate> inviteTemplates = MessageTemplate.find("messageType", inviteType).list();
 * }</pre>
 * 
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li><strong>Subject:</strong> Required, 1-255 characters</li>
 *   <li><strong>Message:</strong> Required, 1-6000 characters</li>
 *   <li><strong>MIME Type:</strong> Required, 1-100 characters</li>
 *   <li><strong>Department:</strong> Optional but recommended for organization</li>
 *   <li><strong>Message Type:</strong> Optional but recommended for classification</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Department
 * @see MessageType
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "message_templates", schema = "survey")
public class MessageTemplate extends PanacheEntityBase {

    /**
     * Unique identifier for the message template.
     * 
     * <p>Auto-generated primary key using a database sequence. This ID uniquely
     * identifies each message template within the system.</p>
     */
    @Id
    @SequenceGenerator(name = "MESSAGE_TEMPLATES_ID_GENERATOR", schema = "survey", sequenceName = "MESSAGE_TEMPLATES_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MESSAGE_TEMPLATES_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public long id;

    /**
     * The department this message template is associated with.
     * 
     * <p>Many-to-one relationship linking the template to a specific department.
     * This allows for department-specific customization of messaging while
     * maintaining organizational relevance and branding consistency.</p>
     * 
     * <p><strong>Purpose:</strong></p>
     * <ul>
     *   <li>Enables department-specific messaging and branding</li>
     *   <li>Allows for organizational customization</li>
     *   <li>Supports role-based access control to templates</li>
     * </ul>
     */
    @ManyToOne
    @JoinColumn(name = "department_id")
    public Department department;

    /**
     * The type/category of this message template.
     * 
     * <p>Many-to-one relationship linking the template to a specific message type
     * classification. This enables categorization of templates by their intended
     * purpose (invitations, reminders, notifications, etc.).</p>
     * 
     * <p><strong>Common Types:</strong></p>
     * <ul>
     *   <li>Survey Invitations</li>
     *   <li>Reminder Messages</li>
     *   <li>Thank You Messages</li>
     *   <li>Administrative Notifications</li>
     * </ul>
     */
    @ManyToOne
    @JoinColumn(name = "message_type_id")
    public MessageType messageType;

    /**
     * The main content/body of the message template.
     * 
     * <p>Contains the primary text content that will be sent to survey participants.
     * This field supports template variables and can contain formatted content
     * based on the specified MIME type.</p>
     * 
     * <p><strong>Content Features:</strong></p>
     * <ul>
     *   <li><strong>Template Variables:</strong> Support for dynamic content insertion</li>
     *   <li><strong>Rich Formatting:</strong> HTML support when MIME type is text/html</li>
     *   <li><strong>Length Limit:</strong> Maximum 6000 characters for comprehensive messaging</li>
     *   <li><strong>Validation:</strong> Required field that cannot be blank</li>
     * </ul>
     * 
     * <p><strong>Example Content:</strong></p>
     * <pre>
     * Dear [PATIENT_NAME],
     * 
     * We hope you are recovering well from your recent visit to [DEPARTMENT_NAME].
     * We would appreciate your feedback about your experience...
     * </pre>
     */
    @Column(name = "message")
    @NotBlank
    @Length(min = 1, max = 6000)
    public String message;

    /**
     * The subject line for the message template.
     * 
     * <p>Used as the email subject line when sending survey communications.
     * Should be concise, descriptive, and engaging to encourage participation.</p>
     * 
     * <p><strong>Best Practices:</strong></p>
     * <ul>
     *   <li><strong>Clarity:</strong> Clearly indicate the purpose of the message</li>
     *   <li><strong>Brevity:</strong> Keep within email client display limits</li>
     *   <li><strong>Personalization:</strong> Include relevant context (department, survey type)</li>
     *   <li><strong>Action-Oriented:</strong> Encourage survey participation</li>
     * </ul>
     * 
     * <p><strong>Examples:</strong> "Patient Experience Survey - Cardiology Department"</p>
     */
    @Column(name = "subject")
    @NotBlank
    @Length(min = 1, max = 255)
    public String subject;

    /**
     * The MIME type specification for the message content.
     * 
     * <p>Defines how the message content should be interpreted and displayed
     * by email clients and other messaging systems. This allows for both
     * plain text and rich HTML content.</p>
     * 
     * <p><strong>Supported Types:</strong></p>
     * <ul>
     *   <li><strong>text/plain:</strong> Plain text messages</li>
     *   <li><strong>text/html:</strong> HTML-formatted messages with rich content</li>
     *   <li><strong>multipart/alternative:</strong> Mixed content types</li>
     * </ul>
     * 
     * <p><strong>Usage Guidelines:</strong></p>
     * <ul>
     *   <li>Use text/plain for simple, accessible messages</li>
     *   <li>Use text/html for branded, formatted communications</li>
     *   <li>Ensure content matches the specified MIME type</li>
     *   <li>Consider accessibility when using HTML formatting</li>
     * </ul>
     */
    @Column(name = "mime_type")
    @NotBlank
    @Length(min = 1, max = 100)
    public String mimeType;
}