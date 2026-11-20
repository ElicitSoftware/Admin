package com.elicitsoftware.service;

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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.model.User;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application-scoped service that owns persistence for {@link Department} entities.
 *
 * <p>Keeping the transactional data-access logic here (rather than in the Vaadin view) lets the
 * UI layer stay focused on presentation and validation, and gives a single, testable seam for
 * department create/update behavior.</p>
 *
 * @author Elicit Software
 * @see Department
 */
@ApplicationScoped
public class DepartmentService {

    /**
     * Default constructor for CDI.
     */
    public DepartmentService() {
        // Default constructor for CDI
    }

    /**
     * Persists a new department or merges changes into an existing one, in a single transaction.
     *
     * <p>A department whose id is {@code 0} is treated as new and inserted; any other id is
     * treated as an update and merged.</p>
     *
     * @param department the department to save; must not be {@code null}
     * @return the managed, saved department instance
     */
    @Transactional
    public Department save(Department department) {
        if (department.id == 0) {
            department.persist();
            return department;
        }
        return Department.getEntityManager().merge(department);
    }

    /**
     * Inserts a new department and assigns it to the administrator creating it, in one
     * transaction (UC-028 BR-113).
     *
     * <p>A fresh deployment has no department, and the administrator who creates the first
     * one must be able to use it at once. Persisting the department and adding it to the
     * creator's departments in the same transaction means an administrator can never end up
     * having created a department they are not assigned to. The rule is unconditional: an
     * administrator who sets up several departments belongs to all of them.</p>
     *
     * <p>An unknown or inactive principal still gets the department persisted; only the
     * assignment is skipped, and the caller's session record is then simply unchanged.</p>
     *
     * @param department    the new department; must have id {@code 0}
     * @param principalName the username of the signed-in administrator
     * @return the managed, persisted department
     */
    @Transactional
    public Department create(Department department, String principalName) {
        department.persist();
        User owner = User.find("username = ?1 and active = true", principalName).firstResult(); // i18n:ignore (JPQL)
        if (owner != null) {
            // The join table is owned by User, so mutating the managed set writes the row on flush.
            // A record persisted in this same persistence context has no set yet.
            if (owner.getDepartments() == null) {
                owner.setDepartments(new HashSet<>());
            }
            owner.getDepartments().add(department);
        }
        return department;
    }

    /**
     * The surveys a department's subjects may be registered onto (UC-006 BR-172), in display
     * order. Read through the join table rather than a mapped collection, so a view holding a
     * detached department can ask without a lazy-loading failure.
     *
     * @param departmentId the department
     * @return its surveys; empty when it has none or does not exist
     */
    @Transactional
    public List<Survey> surveysOf(long departmentId) {
        Set<Integer> ids = surveyIdsOf(departmentId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return Survey.<Survey>list("id in ?1", Sort.by("displayOrder"), ids); // i18n:ignore (JPQL)
    }

    /**
     * Every installed survey, in display order: what the department screen offers (UC-006).
     *
     * @return the installed surveys
     */
    @Transactional
    public List<Survey> installedSurveys() {
        return Survey.listAll(Sort.by("displayOrder"));
    }

    /**
     * The ids of a department's surveys (UC-006 BR-172).
     *
     * @param departmentId the department
     * @return the ids of its surveys
     */
    @Transactional
    public Set<Integer> surveyIdsOf(long departmentId) {
        @SuppressWarnings("unchecked")
        List<Number> rows = Department.getEntityManager()
                .createNativeQuery("SELECT survey_id FROM survey.department_surveys WHERE department_id = ?1") // i18n:ignore (SQL)
                .setParameter(1, departmentId)
                .getResultList();
        return rows.stream().map(Number::intValue).collect(Collectors.toSet());
    }

    /**
     * Whether a subject in this department may be registered onto this survey (UC-006 BR-172,
     * UC-003 BR-174, UC-010 BR-177). False for a survey that is not installed.
     *
     * @param departmentId the subject's department
     * @param surveyId     the survey it is to be registered onto
     * @return true when the department lists the survey
     */
    @Transactional
    public boolean offersSurvey(long departmentId, long surveyId) {
        Number count = (Number) Department.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM survey.department_surveys " // i18n:ignore (SQL)
                        + "WHERE department_id = ?1 AND survey_id = ?2") // i18n:ignore (SQL)
                .setParameter(1, departmentId)
                .setParameter(2, surveyId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    /**
     * Replaces a department's surveys with the given ones (UC-006 BR-172).
     *
     * @param departmentId the department, already persisted
     * @param surveyIds    the surveys it may register onto from now on
     */
    @Transactional
    public void setSurveys(long departmentId, Collection<Integer> surveyIds) {
        var em = Department.getEntityManager();
        em.createNativeQuery("DELETE FROM survey.department_surveys WHERE department_id = ?1") // i18n:ignore (SQL)
                .setParameter(1, departmentId)
                .executeUpdate();
        for (Integer surveyId : new HashSet<>(surveyIds)) {
            em.createNativeQuery("INSERT INTO survey.department_surveys (department_id, survey_id) VALUES (?1, ?2)") // i18n:ignore (SQL)
                    .setParameter(1, departmentId)
                    .setParameter(2, surveyId)
                    .executeUpdate();
        }
    }

    /**
     * Saves an existing department and its surveys in one transaction (UC-006).
     *
     * @param department the department, as edited
     * @param surveyIds  the surveys it may register onto
     * @return the merged department
     */
    @Transactional
    public Department save(Department department, Collection<Integer> surveyIds) {
        Department saved = save(department);
        setSurveys(saved.id, surveyIds);
        return saved;
    }

    /**
     * Creates a department, assigns it to its creator (UC-028 BR-113) and gives it its surveys
     * (UC-006 BR-173), in one transaction.
     *
     * @param department    the new department
     * @param principalName the creator
     * @param surveyIds     the surveys it may register onto
     * @return the persisted department
     */
    @Transactional
    public Department create(Department department, String principalName, Collection<Integer> surveyIds) {
        Department created = create(department, principalName);
        setSurveys(created.id, surveyIds);
        return created;
    }
}
