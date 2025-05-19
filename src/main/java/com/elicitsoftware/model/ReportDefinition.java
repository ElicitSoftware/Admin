package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * The persistent class for the RESPONDENT database table.
 */
@Entity
@Table(name = "reports", schema = "survey")
public class ReportDefinition extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "REPORT_ID_GENERATOR", schema = "survey", sequenceName = "reports_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "REPORT_ID_GENERATOR")
    @Column(name = "id", unique = true, nullable = false)
    public Integer id;

    @JsonbTransient
    @ManyToOne()
    @JoinColumn(name = "survey_id", nullable = false)
    public Survey survey;

    @Column(name = "name")
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "url")
    public String url;

    @Column(name = "display_order")
    public int displayOrder;

}
