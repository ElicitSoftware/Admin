package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

@Entity
@Table(name = "message_templates", schema = "survey")
public class MessageTemplate extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "MESSAGE_TEMPLATES_ID_GENERATOR", schema = "survey", sequenceName = "MESSAGE_TEMPLATES_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MESSAGE_TEMPLATES_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public long id;

    @ManyToOne
    @JoinColumn(name = "department_id")
    public Department department;

    @ManyToOne
    @JoinColumn(name = "message_type_id")
    public MessageType messageType;

    @Column(name = "message")
    @NotBlank
    @Length(min = 1, max = 6000)
    public String message;

    @Column(name = "subject")
    @NotBlank
    @Length(min = 1, max = 255)
    public String subject;

    @Column(name = "mime_type")
    @NotBlank
    @Length(min = 1, max = 100)
    public String mimeType;
}