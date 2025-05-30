package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "departments", schema = "survey")
public class Department extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "departments_id_generator", schema = "survey", sequenceName = "departments_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "departments_id_generator")
    @Column(unique = true, nullable = false, precision = 20)
    public long id;

    @Column(name = "name")
    public String name;

    @Column(name = "code")
    public String code;

    @Column(name = "default_message_id")
    public String defaultMessageId;

    public String getName() {
        return name;
    }
}
