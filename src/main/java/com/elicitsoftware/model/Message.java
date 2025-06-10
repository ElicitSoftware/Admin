package com.elicitsoftware.model;

import com.elicitsoftware.exception.TokenGenerationError;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

import java.util.ArrayList;
import java.util.Date;

@Entity
@Table(name = "messages", schema = "survey")
public class Message extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "MESSAGES_ID_GENERATOR", schema = "survey", sequenceName = "messages_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MESSAGES_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false)
    public Long id;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    @NotNull(message = "Subject must not be null")
    public Subject subject;

    @ManyToOne
    @JoinColumn(name = "message_type", nullable = false)
    @NotNull(message = "Message type must not be null")
    public MessageType messageType;

    @Column(name = "mime_type", nullable = false, length = 100)
    @NotNull(message = "Mime type must not be null")
    public String mimeType = "text/html";

    @Column(name = "subjectline", nullable = false, length = 255)
    @NotBlank(message = "Subject cannot be blank")
    @Length(max = 255, message = "Subject max length 255 characters")
    public String subjectLine;

    @Column(name = "body", nullable = false, length = 6000)
    @NotBlank(message = "Body cannot be blank")
    @Length(max = 6000, message = "Body max length 50 characters")
    public String body;

    @Column(name = "created_dt", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    public Date createdDt = new Date();

    @Column(name = "sent_dt")
    @Temporal(TemporalType.TIMESTAMP)
    public Date sentDt;

    public Message() {
        super();
    }

    public Message(Subject subject, MessageType messageType, String subjectLine, String body) {
        this.subject = subject;
        this.messageType = messageType;
        this.subjectLine = subjectLine;
        this.body = body;
        this.createdDt = new Date();
    }

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