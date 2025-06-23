package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/**
 * JPA entity representing message type classifications in the Elicit survey system.
 * 
 * <p>This entity provides a categorization system for different types of messages
 * used in survey communications. Message types help organize and standardize
 * the various communication templates used throughout the survey lifecycle.</p>
 * 
 * <p><strong>Purpose:</strong></p>
 * <ul>
 *   <li><strong>Template Organization:</strong> Categorizes message templates by purpose</li>
 *   <li><strong>Communication Workflow:</strong> Supports structured communication processes</li>
 *   <li><strong>Administrative Management:</strong> Enables type-based template management</li>
 *   <li><strong>System Integration:</strong> Provides consistent message categorization</li>
 * </ul>
 * 
 * <p><strong>Common Message Types:</strong></p>
 * <ul>
 *   <li><strong>Invitation:</strong> Initial survey invitations to participants</li>
 *   <li><strong>Reminder:</strong> Follow-up reminders for incomplete surveys</li>
 *   <li><strong>Thank You:</strong> Confirmation messages after survey completion</li>
 *   <li><strong>Notification:</strong> Administrative notifications and updates</li>
 *   <li><strong>Follow-up:</strong> Post-survey follow-up communications</li>
 * </ul>
 * 
 * <p><strong>Relationships:</strong></p>
 * <ul>
 *   <li><strong>MessageTemplate:</strong> One-to-many relationship with message templates</li>
 *   <li><strong>Survey Communications:</strong> Used to categorize survey-related messaging</li>
 * </ul>
 * 
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new message type
 * MessageType invitationType = new MessageType();
 * invitationType.setName("Survey Invitation");
 * invitationType.persist();
 * 
 * // Find message type by name
 * MessageType type = MessageType.find("name", "Survey Invitation").firstResult();
 * 
 * // Get all message types
 * List<MessageType> allTypes = MessageType.listAll();
 * }</pre>
 * 
 * <p><strong>Administrative Benefits:</strong></p>
 * <ul>
 *   <li><strong>Template Management:</strong> Organize templates by communication purpose</li>
 *   <li><strong>Workflow Standardization:</strong> Ensure consistent messaging approaches</li>
 *   <li><strong>Reporting:</strong> Analyze communication effectiveness by type</li>
 *   <li><strong>Compliance:</strong> Maintain appropriate messaging for different contexts</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see MessageTemplate
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "message_types", schema = "survey")
public class MessageType extends PanacheEntityBase {

    /**
     * Unique identifier for the message type.
     * 
     * <p>Auto-generated primary key using a database sequence. This ID uniquely
     * identifies each message type within the system and is used for establishing
     * relationships with message templates.</p>
     */
    @Id
    @SequenceGenerator(name = "MESSAGE_TYPES_ID_GENERATOR", schema = "survey", sequenceName = "MESSAGE_TYPES_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MESSAGE_TYPES_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    private long id;

    /**
     * Descriptive name of the message type.
     * 
     * <p>Human-readable name that describes the purpose and context of this
     * message type. This name is used in administrative interfaces for
     * template management and categorization.</p>
     * 
     * <p><strong>Examples:</strong></p>
     * <ul>
     *   <li>"Survey Invitation" - Initial invitations to participate</li>
     *   <li>"Reminder Notice" - Follow-up reminders for incomplete surveys</li>
     *   <li>"Thank You Message" - Confirmation after survey completion</li>
     *   <li>"Administrative Notification" - System or administrative updates</li>
     * </ul>
     * 
     * <p><strong>Guidelines:</strong></p>
     * <ul>
     *   <li>Should be descriptive and clear</li>
     *   <li>Should indicate the message's purpose</li>
     *   <li>Should be unique within the system</li>
     *   <li>Should follow organizational naming conventions</li>
     * </ul>
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Gets the unique identifier for this message type.
     * 
     * @return the message type ID
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this message type.
     * 
     * @param id the message type ID to set
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Gets the descriptive name of this message type.
     * 
     * @return the message type name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the descriptive name of this message type.
     * 
     * @param name the message type name to set
     */
    public void setName(String name) {
        this.name = name;
    }
}
