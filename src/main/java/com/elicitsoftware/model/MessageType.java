package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "message_types", schema = "survey")
public class MessageType extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "MESSAGE_TYPES_ID_GENERATOR", schema = "survey", sequenceName = "MESSAGE_TYPES_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MESSAGE_TYPES_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    private long id;

    @Column(name = "name", nullable = false)
    private String name;

}
