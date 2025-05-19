package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "ROLES", schema = "survey")
public class Role extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "ROLES_ID_GENERATOR", schema = "survey", sequenceName = "ROLES_SEQ", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ROLES_ID_GENERATOR")
    @Column(unique = true, nullable = false, precision = 20)
    public long id;

    @Column(name = "NAME")
    public String name;

}
