package com.elicitsoftware.service;

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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Status;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.rest.AccessCodeService;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted test for {@link CsvImportService#importSubjects(InputStream)}.
 *
 * <p>Traceability: UC-010 (Register Subjects via Integration API), which covers CSV upload as one
 * of the three ways to register subjects. Closes the coverage gap flagged in the 2026-09-03
 * best-practices audit: {@code CsvImportService} had zero test coverage.</p>
 *
 * <p>{@code importSubjects} delegates each row to the real, CDI-intercepted
 * {@link AccessCodeService#putSubject}, which is {@code @RolesAllowed}
 * ({@code elicit_importer}, {@code elicit_admin}, {@code elicit_user}) — that check applies to
 * this direct bean-to-bean call just as it would to an HTTP request, so every test method needs
 * {@code @TestSecurity} with one of those roles.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CsvImportServiceTest {

    @Inject
    CsvImportService csvImportService;

    private Department persistDepartment(String token) {
        Department department = new Department();
        department.name = "CSV Import Dept " + token;
        department.code = "CSV-" + token;
        // "1" need not resolve to a real MessageTemplate row: Message.createMessagesForSubject
        // just logs and skips an unresolvable template id rather than failing the import.
        department.defaultMessageId = "1";
        department.fromEmail = "csv-import@example.org";
        department.persist();
        return department;
    }

    private InputStream toStream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** UC-010: two well-formed rows are both imported and become queryable respondent statuses. */
    @Test
    @TestTransaction
    @TestSecurity(user = "importer", roles = {"elicit_importer"})
    void importSubjectsSucceedsForWellFormedCsv() throws Exception {
        Department dept = persistDepartment("WELLFORMED");
        String csv = String.join("\n",
                dept.id + ",Alice,Anderson,,1990-01-01,alice@example.org,555-010-0100,CSV-XID-1",
                dept.id + ",Bob,Baker,,02/15/1985,bob@example.org,555-010-0101,CSV-XID-2");

        AddResponse response = csvImportService.importSubjects(toStream(csv));

        assertEquals(2, response.getSubjects().size());
        assertNotNull(Status.findByXidAndDepartmentId("CSV-XID-1", (int) dept.id));
        assertNotNull(Status.findByXidAndDepartmentId("CSV-XID-2", (int) dept.id));
    }

    /** UC-010: comment lines and blank lines are skipped rather than counted as row errors. */
    @Test
    @TestTransaction
    @TestSecurity(user = "importer", roles = {"elicit_importer"})
    void importSubjectsSkipsCommentsAndBlankLines() throws Exception {
        Department dept = persistDepartment("COMMENTS");
        String csv = String.join("\n",
                "# departmentId,firstName,lastName,middleName,dob,email,phone,xid",
                "",
                dept.id + ",Carla,Cruz,,1992-03-04,carla@example.org,555-010-0102,CSV-XID-3",
                "   ",
                "# another comment",
                dept.id + ",Dan,Diaz,,1988-07-22,dan@example.org,555-010-0103,CSV-XID-4");

        AddResponse response = csvImportService.importSubjects(toStream(csv));

        assertEquals(2, response.getSubjects().size());
        assertNotNull(Status.findByXidAndDepartmentId("CSV-XID-3", (int) dept.id));
        assertNotNull(Status.findByXidAndDepartmentId("CSV-XID-4", (int) dept.id));
    }

    /**
     * UC-010: a later line failing validation is reported by line number and does not prevent
     * an earlier, valid line from being persisted. {@code importSubjects} is {@code @Transactional}
     * but throws a checked {@code Exception}, which the Jakarta Transactions default does not mark
     * for rollback, so the earlier row's insert survives.
     */
    @Test
    @TestTransaction
    @TestSecurity(user = "importer", roles = {"elicit_importer"})
    void importSubjectsThrowsForLineErrorsButPersistsValidRows() {
        Department dept = persistDepartment("PARTIAL");
        String csv = String.join("\n",
                dept.id + ",Erin,Evans,,1991-05-05,erin@example.org,555-010-0104,CSV-XID-5",
                dept.id + ",NoLastName,,,1991-05-05,,555-010-0105,CSV-XID-6");

        Exception ex = assertThrows(Exception.class, () -> csvImportService.importSubjects(toStream(csv)));

        assertTrue(ex.getMessage().contains("Line 2: Last name is required"), ex.getMessage());
        assertNotNull(Status.findByXidAndDepartmentId("CSV-XID-5", (int) dept.id),
                "the valid row before the bad line should still have been committed");
    }

    /**
     * UC-010: a quoted field containing a comma (the middle name) is parsed as a single field by
     * {@code splitCsvLine} rather than being split into two, all the way through to the persisted
     * respondent status.
     */
    @Test
    @TestTransaction
    @TestSecurity(user = "importer", roles = {"elicit_importer"})
    void importSubjectsHandlesQuotedFieldsWithEmbeddedCommas() throws Exception {
        Department dept = persistDepartment("QUOTED");
        String csv = dept.id + ",Frank,Foster,\"Middle, Q\",1980-12-25,frank@example.org,555-010-0106,CSV-XID-7";

        AddResponse response = csvImportService.importSubjects(toStream(csv));

        assertEquals(1, response.getSubjects().size());
        Status status = Status.findByXidAndDepartmentId("CSV-XID-7", (int) dept.id);
        assertNotNull(status);
        assertEquals("Middle, Q", status.getMiddleName());
    }
}
