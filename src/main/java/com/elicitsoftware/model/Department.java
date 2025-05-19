package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Set;

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

    @Column(name = "default_message_type_id")
    public String defaultMessageTypeId;

    public String getName() {
        return name;
    }
}
