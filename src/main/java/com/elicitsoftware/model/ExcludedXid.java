package com.elicitsoftware.model;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA entity representing excluded XIDs in the Elicit survey system.
 *
 * <p>This entity models XIDs (external identifiers) that should be excluded from
 * survey import processes for specific departments. The exclusion list helps prevent
 * unwanted or problematic data from being processed during survey data imports.</p>
 *
 * <p><strong>Key Functions:</strong></p>
 * <ul>
 *   <li><strong>Import Control:</strong> Prevents specific XIDs from being imported</li>
 *   <li><strong>Department Isolation:</strong> Exclusions are department-specific</li>
 *   <li><strong>Audit Trail:</strong> Tracks when and why exclusions were added</li>
 *   <li><strong>Data Quality:</strong> Helps maintain clean survey data</li>
 * </ul>
 *
 * <p><strong>Database Mapping:</strong></p>
 * <ul>
 *   <li><strong>Table:</strong> {@code survey.excluded_xids}</li>
 *   <li><strong>Primary Key:</strong> Integer ID</li>
 *   <li><strong>Unique Constraints:</strong> XID and department combination must be unique</li>
 * </ul>
 *
 * <p><strong>Relationships:</strong></p>
 * <ul>
 *   <li><strong>Departments:</strong> Associated with specific departments via department ID foreign key</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong></p>
 * <pre>{@code
 * // Create a new exclusion
 * ExcludedXid exclusion = new ExcludedXid();
 * exclusion.xid = "PROB123";
 * exclusion.departmentId = 1;
 * exclusion.reason = "Test data not for production";
 * exclusion.createdBy = "admin@example.com";
 * exclusion.persist();
 *
 * // Find exclusions by department
 * List<ExcludedXid> exclusions = ExcludedXid.findByDepartmentId(1);
 *
 * // Check if XID is excluded for a department
 * boolean isExcluded = ExcludedXid.isExcluded("XID123", 1);
 * }</pre>
 *
 * <p><strong>Business Rules:</strong></p>
 * <ul>
 *   <li><strong>Uniqueness:</strong> Each XID can only be excluded once per department</li>
 *   <li><strong>Department Scope:</strong> Exclusions are department-specific</li>
 *   <li><strong>Audit Requirements:</strong> Creation timestamp is automatically set</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Department
 * @see PanacheEntityBase
 */
@Entity
@Table(name = "excluded_xids", schema = "survey",
       uniqueConstraints = @UniqueConstraint(name = "excluded_xids_xid_dept_un", 
                                           columnNames = {"xid", "department"}))
public class ExcludedXid extends PanacheEntityBase {

    /**
     * Unique identifier for the excluded XID record.
     *
     * <p>Primary key for the excluded XIDs table. This ID uniquely identifies
     * each exclusion record within the system.</p>
     *
     * <p><strong>Database Mapping:</strong></p>
     * <ul>
     *   <li><strong>Column Type:</strong> INTEGER</li>
     *   <li><strong>Nullable:</strong> No</li>
     *   <li><strong>Auto-generated:</strong> Yes, using sequence</li>
     * </ul>
     */
    @Id
    @SequenceGenerator(name = "excluded_xids_id_generator", schema = "survey", 
                      sequenceName = "excluded_xids_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "excluded_xids_id_generator")
    @Column(unique = true, nullable = false)
    public Integer id;

    /**
     * The external identifier (XID) to be excluded.
     *
     * <p>The specific XID that should be excluded from import processes
     * for the associated department. This represents an external identifier
     * from source systems that should not be processed.</p>
     *
     * <p><strong>Usage Guidelines:</strong></p>
     * <ul>
     *   <li>Must be unique per department (enforced by database constraint)</li>
     *   <li>Case-sensitive matching during import filtering</li>
     *   <li>Should match the format of XIDs in source systems</li>
     * </ul>
     */
    @Column(name = "xid", nullable = false, length = 255)
    public String xid;

    /**
     * The department ID for which this XID exclusion applies.
     *
     * <p>Integer field that stores the department ID as a foreign key reference.
     * This field directly maps to the department column in the database.</p>
     *
     * <p><strong>Usage Guidelines:</strong></p>
     * <ul>
     *   <li>Should correspond to valid department IDs in the departments table</li>
     *   <li>Part of the unique constraint with XID</li>
     *   <li>Used to scope exclusions to specific departments</li>
     *   <li>Simple integer field for direct database mapping</li>
     * </ul>
     */
    @Column(name = "department", nullable = false)
    public Integer departmentId;

    /**
     * Optional reason for the exclusion.
     *
     * <p>Human-readable explanation of why this XID is excluded. This field
     * helps with audit trails and understanding the business logic behind
     * exclusions.</p>
     *
     * <p><strong>Examples:</strong></p>
     * <ul>
     *   <li>"Test data not for production use"</li>
     *   <li>"Duplicate record with data quality issues"</li>
     *   <li>"Privacy request - do not process"</li>
     *   <li>"Invalid XID format from legacy system"</li>
     * </ul>
     */
    @Column(name = "reason", length = 500)
    public String reason;

    /**
     * Timestamp when this exclusion record was created.
     *
     * <p>Automatically populated with the current timestamp when the record
     * is inserted into the database. This provides an audit trail of when
     * exclusions were added to the system.</p>
     *
     * <p><strong>Database Behavior:</strong></p>
     * <ul>
     *   <li>Automatically set to CURRENT_TIMESTAMP on insert</li>
     *   <li>Timezone-aware (TIMESTAMP WITH TIME ZONE)</li>
     *   <li>Cannot be null</li>
     * </ul>
     */
    @Column(name = "created_dt", nullable = false)
    public OffsetDateTime createdDt;

    /**
     * Optional identifier of who created this exclusion.
     *
     * <p>Records the user or system that added this exclusion. This can be
     * an email address, username, or system identifier for audit purposes.</p>
     *
     * <p><strong>Usage Guidelines:</strong></p>
     * <ul>
     *   <li>Typically an email address or username</li>
     *   <li>Used for audit and accountability</li>
     *   <li>Can be null for system-generated exclusions</li>
     * </ul>
     */
    @Column(name = "created_by", length = 100)
    public String createdBy;

    /**
     * Default constructor for JPA entity instantiation.
     * <p>
     * Creates a new ExcludedXid instance with default values. This constructor
     * is required by JPA for entity instantiation and is used by the
     * persistence framework during query execution.
     */
    public ExcludedXid() {
        // Default constructor for JPA
    }

    /**
     * Constructor with required fields.
     *
     * <p>Creates a new ExcludedXid with the minimum required information.
     * The creation timestamp will be automatically set by the database.</p>
     *
     * @param xid the external identifier to exclude
     * @param departmentId the department ID for which to apply the exclusion
     */
    public ExcludedXid(String xid, Integer departmentId) {
        this.xid = xid;
        this.departmentId = departmentId;
    }

    /**
     * Constructor with all fields except timestamps.
     *
     * <p>Creates a new ExcludedXid with complete information. The creation
     * timestamp will be automatically set by the database.</p>
     *
     * @param xid the external identifier to exclude
     * @param departmentId the department ID for which to apply the exclusion
     * @param reason optional reason for the exclusion
     * @param createdBy optional identifier of who created the exclusion
     */
    public ExcludedXid(String xid, Integer departmentId, String reason, String createdBy) {
        this.xid = xid;
        this.departmentId = departmentId;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    /**
     * Returns the XID that is excluded.
     *
     * @return the excluded XID, may be null if not set
     */
    public String getXid() {
        return xid;
    }

    /**
     * Sets the XID to be excluded.
     *
     * @param xid the XID to exclude
     */
    public void setXid(String xid) {
        this.xid = xid;
    }

    /**
     * Returns the department ID for which this exclusion applies.
     *
     * @return the department ID, may be null if not set
     */
    public Integer getDepartmentId() {
        return departmentId;
    }

    /**
     * Sets the department ID for which this exclusion applies.
     *
     * @param departmentId the department ID
     */
    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    /**
     * Returns the reason for the exclusion.
     *
     * @return the exclusion reason, may be null if not provided
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the reason for the exclusion.
     *
     * @param reason the exclusion reason
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return the creation timestamp, may be null if not set
     */
    public OffsetDateTime getCreatedDt() {
        return createdDt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdDt the creation timestamp
     */
    public void setCreatedDt(OffsetDateTime createdDt) {
        this.createdDt = createdDt;
    }

    /**
     * Returns who created this exclusion.
     *
     * @return the creator identifier, may be null if not provided
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets who created this exclusion.
     *
     * @param createdBy the creator identifier
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Checks if a specific XID is excluded for a given department.
     *
     * <p>Convenience method to determine if an XID should be excluded
     * during import operations for a specific department.</p>
     *
     * @param xid the XID to check
     * @param departmentId the department ID to check against
     * @return true if the XID is excluded for the department, false otherwise
     */
    public static boolean isExcluded(String xid, Integer departmentId) {
        return count("xid = ?1 and departmentId = ?2", xid, departmentId) > 0;
    }

    /**
     * Finds all exclusions for a specific department by ID.
     *
     * <p>Retrieves all XIDs that are excluded for the given department ID,
     * useful for displaying exclusion lists or bulk operations.</p>
     *
     * @param departmentId the department ID to search for
     * @return list of excluded XIDs for the department
     */
    public static java.util.List<ExcludedXid> findByDepartmentId(Integer departmentId) {
        return find("departmentId", departmentId).list();
    }

    /**
     * Finds all exclusions for a specific XID across all departments.
     *
     * <p>Retrieves all department exclusions for the given XID, useful
     * for understanding the scope of an XID's exclusion.</p>
     *
     * @param xid the XID to search for
     * @return list of exclusions for the XID across departments
     */
    public static java.util.List<ExcludedXid> findByXid(String xid) {
        return find("xid", xid).list();
    }
}