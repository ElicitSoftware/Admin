package com.elicitsoftware.model;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.util.Set;

/**
 * A machine client of the registration API (UC-032): the principal name its bearer tokens
 * carry and the departments it may register into (UC-010 BR-137).
 *
 * <p>An import client is not a {@link User}. It has no first or last name, holds no row in
 * {@code survey.user_roles}, and its record gives no access to the console (BR-168); the role
 * {@code elicit_importer} comes from the identity provider.</p>
 */
@Entity
@Table(name = "import_clients", schema = "survey")
public class ImportClient extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "import_clients_id_generator", schema = "survey", sequenceName = "import_clients_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "import_clients_id_generator")
    @Column(unique = true, nullable = false, precision = 20)
    private long id;

    /** The principal name the client's tokens carry (BR-165). */
    @Column(name = "name")
    private String name;

    /** What the client is, for the administrator. */
    @Column(name = "description")
    private String description;

    @Column(name = "active")
    private boolean active;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "import_client_departments", schema = "survey", joinColumns = {
            @JoinColumn(name = "import_client_id")
    }, inverseJoinColumns = {
            @JoinColumn(name = "department_id")
    })
    @OrderBy(value = "name")
    private Set<Department> departments;

    /** Default constructor for JPA. */
    public ImportClient() {
        // Default constructor
    }

    /**
     * Finds the import client a principal name belongs to, active or not.
     *
     * @param name the principal name
     * @return the import client, or null when no import client holds the name
     */
    public static ImportClient findByName(String name) {
        return find("name = ?1", name).firstResult(); // i18n:ignore (JPQL)
    }

    /** @return the unique identifier, 0 for a record not yet stored */
    public long getId() {
        return id;
    }

    /** @param id the unique identifier */
    public void setId(long id) {
        this.id = id;
    }

    /** @return the principal name the client's tokens carry */
    public String getName() {
        return name;
    }

    /** @param name the principal name the client's tokens carry */
    public void setName(String name) {
        this.name = name;
    }

    /** @return the description, or null */
    public String getDescription() {
        return description;
    }

    /** @param description the description, or null */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return whether the client may register (BR-167) */
    public boolean isActive() {
        return active;
    }

    /** @param active whether the client may register */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** @return the departments the client may register into */
    public Set<Department> getDepartments() {
        return departments;
    }

    /** @param departments the departments the client may register into */
    public void setDepartments(Set<Department> departments) {
        this.departments = departments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImportClient other)) {
            return false;
        }
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
