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

import com.elicitsoftware.model.Status;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.response.AddResponseStatus;
import com.elicitsoftware.rest.AccessCodeService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-012: Import Respondent Data (subject CSV upload) and UC-010: Register Subjects via
 * Integration API — how {@link CsvImportService} parses each CSV line into a registration request
 * and reports bad lines, with the access-code service stubbed (no database).
 */
class CsvImportServiceUnitTest {

    private final List<AddRequest> registered = new ArrayList<>();

    /** Department ids the caller is not assigned to (UC-010 BR-137); none unless a test says so. */
    private final List<Integer> foreignDepartments = new ArrayList<>();

    /** Department/survey pairs the department does not register onto (UC-006 BR-172). */
    private final java.util.Set<String> notOffered = new java.util.HashSet<>();

    private CsvImportService service(Function<AddRequest, AddResponse> answer) {
        AccessCodeService accessCodes = new AccessCodeService() {
            @Override
            public AddResponse registerSubject(AddRequest request) {
                registered.add(request);
                return answer.apply(request);
            }

            @Override
            public boolean permitsDepartment(long departmentId) {
                return !foreignDepartments.contains((int) departmentId);
            }

            @Override
            public boolean offersSurvey(long departmentId, long surveyId) {
                return !notOffered.contains(departmentId + "/" + surveyId);
            }
        };
        return new CsvImportService(accessCodes);
    }

    private static AddResponse registeredAs(String importStatus) {
        AddResponse response = new AddResponse();
        response.addStatus(new AddResponseStatus(new Status(), importStatus));
        return response;
    }

    private static AddResponse importCsv(CsvImportService service, String csv) throws Exception {
        return service.importSubjects(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private List<String> lineErrors(String csv) {
        CsvImportException e = assertThrows(CsvImportException.class,
                () -> importCsv(service(r -> registeredAs("New Subject")), csv));
        assertEquals(CsvImportException.HEADLINE + "\n" + String.join("\n", e.getLineErrors()), e.getMessage());
        return e.getLineErrors();
    }

    @Test
    void given_validLines_when_imported_then_eachBecomesARegistrationRequest() throws Exception {
        AddResponse response = importCsv(service(r -> registeredAs("New Subject " + r.xid)),
                "# surveyId,departmentId,xid,...,phone\n\n 7 , 1 , X1 , Jane , Doe ,, 1980-02-03 , j@x.org , 555-010-0100 \n"
                        + "4,2,,\"Doe, Jr\",Smith,M,02/03/1981,a@x.org,\n");

        assertEquals(2, registered.size());
        AddRequest first = registered.get(0);
        assertEquals(1, first.departmentId);
        assertEquals(7, first.surveyId, "UC-003 BR-175: the row's own survey, from its first column");
        assertEquals("Jane", first.firstName);
        assertEquals("Doe", first.lastName);
        assertEquals("", first.middleName);
        assertEquals(LocalDate.of(1980, 2, 3), first.dob);
        assertEquals("j@x.org", first.email);
        assertEquals("555-010-0100", first.phone);
        assertEquals("X1", first.xid);

        AddRequest second = registered.get(1);
        assertEquals(2, second.departmentId);
        assertEquals(4, second.surveyId);
        assertEquals("Doe, Jr", second.firstName, "a quoted field keeps its comma");
        assertEquals("M", second.middleName);
        assertEquals(LocalDate.of(1981, 2, 3), second.dob);
        assertEquals("", second.phone);
        assertEquals("", second.xid);

        assertEquals(2, response.getSubjects().size());
        assertEquals("New Subject X1", response.getSubjects().get(0).getImportStatus());
        assertEquals("New Subject ", response.getSubjects().get(1).getImportStatus());
        assertTrue(response.getErrors().isEmpty());
    }

    @Test
    void given_aLineWithoutDateOfBirth_when_imported_then_theDateIsLeftEmpty() throws Exception {
        importCsv(service(r -> registeredAs("ok")), "1,3,,A,B,C,,e@x.org,\n");
        assertNull(registered.get(0).dob);
        assertEquals("", registered.get(0).phone);
        assertEquals("", registered.get(0).xid);
    }

    /** UC-003 BR-175: a row without phone may end at the email. */
    @Test
    void given_aLineEndingAtTheEmail_when_imported_then_thePhoneIsLeftEmpty() throws Exception {
        importCsv(service(r -> registeredAs("ok")), "1,3,X1,A,B,C,,e@x.org\n");
        assertEquals("e@x.org", registered.get(0).email);
        assertNull(registered.get(0).phone);
    }

    /** UC-010 BR-036: the email is required, so a row must reach it. */
    @Test
    void given_aLineShortOfTheEmail_when_imported_then_itIsRefusedAsBadlyFormatted() {
        assertEquals(List.of("Line 1: Invalid CSV format. Expected at least 8 fields: "
                + "surveyId,departmentId,xid,firstName,lastName,middleName,dob,email,phone"),
                lineErrors("1,3,X1,A,B,C,\n"));
        assertTrue(registered.isEmpty());
    }

    /** UC-010 BR-035: a file in the V2 order (departmentId first, no survey) registers no one. */
    @Test
    void given_aFileInTheV2Order_when_imported_then_everyLineIsRefusedAndNoOneIsRegistered() {
        List<String> errors = lineErrors("#departmentId,firstName,lastName,middleName,dob,email,phone,xid\n"
                + "1,Jane,Doe,,1980-02-03,j@x.org,555,X1\n"
                + "1,John,Roe,,1981-04-05,r@x.org,,X2\n");
        assertEquals(List.of("Line 2: Invalid department ID format", "Line 3: Invalid department ID format"), errors);
        assertTrue(registered.isEmpty());
    }

    @Test
    void given_aRefusedRegistration_when_imported_then_itsErrorIsCarriedOnTheResponse() throws Exception {
        AddResponse refused = new AddResponse();
        refused.setError("invalid departmentid");
        refused.setError("second");
        AddResponse response = importCsv(service(r -> refused), "1,9,,A,B,C,,e@x.org,\n");
        assertEquals(List.of("invalid departmentid"), response.getErrors());
        assertTrue(response.getSubjects().isEmpty());
    }

    @Test
    void given_badLines_when_imported_then_eachIsReportedWithItsLineNumber() {
        List<String> errors = lineErrors("1,x,,a,b,c,,e@x.org,\n"
                + "1,1,,a,b,c,31/31/2020,e@x.org,\n"
                + "1,1,, ,b,c,,e@x.org,\n"
                + "1,1,,a, ,c,,e@x.org,\n"
                + "1,1,,a,b,c,, ,\n"
                + "one,1,,a,b,c,,e@x.org,\n"
                + "1,1,,a,b,c,,e@x.org,\n");
        assertEquals(List.of(
                "Line 1: Invalid department ID format",
                "Line 2: Invalid date format. Use yyyy-MM-dd or MM/dd/yyyy",
                "Line 3: First name is required",
                "Line 4: Last name is required",
                "Line 5: Email is required",
                "Line 6: Invalid survey ID format"), errors);
        assertEquals(1, registered.size(), "only the valid line reached registration");
    }

    /** UC-003 BR-175, UC-010 BR-178: one row without its survey rejects the file; no one is registered. */
    @Test
    void given_aLineWithoutItsSurvey_when_imported_then_theWholeFileIsRejected() {
        CsvSurveyMissingException e = assertThrows(CsvSurveyMissingException.class,
                () -> importCsv(service(r -> registeredAs("New Subject")),
                        "1,1,,a,b,c,,e@x.org,\n,1,,d,e,f,,g@x.org\n# note\n ,1,X,h,i,j,,k@x.org,\n"));
        assertEquals(List.of(2, 4), e.getLines());
        assertEquals("Rejected: every row must name its survey in the first column, surveyId. Nothing was imported. "
                + "Lines without it: 2, 4", e.getMessage());
        assertTrue(registered.isEmpty(), "nothing is registered from a rejected file");
    }

    @Test
    void given_aLineForADepartmentTheCallerIsNotAssignedTo_when_imported_then_itIsRefusedByNumberAndTheOthersGoOn() {
        foreignDepartments.add(8);

        List<String> errors = lineErrors("1,1,,a,b,c,,e@x.org,\n"
                + "1,8,,a,b,c,,e@x.org,\n"
                + "1,1,,d,e,f,,g@x.org,\n");

        assertEquals(List.of("Line 2: You are not assigned to department 8"), errors);
        assertEquals(List.of(1, 1), registered.stream().map(r -> r.departmentId).toList(),
                "BR-137: the refused line never reaches registration; BR-037: the others do");
    }

    /** UC-006 BR-172, UC-010 BR-177: a line whose department does not register onto its survey. */
    @Test
    void given_aLineWhoseDepartmentLacksItsSurvey_when_imported_then_itIsRefusedByNumberAndTheOthersGoOn() {
        notOffered.add("1/9");

        List<String> errors = lineErrors("1,1,,a,b,c,,e@x.org,\n"
                + "9,1,,a,b,c,,e@x.org,\n");

        assertEquals(List.of("Line 2: Department 1 does not register subjects onto survey 9"), errors);
        assertEquals(List.of(1), registered.stream().map(r -> r.surveyId).toList());
    }

    /** UC-003 A6 step 2: the plan registers no one and says what an import would do. */
    @Test
    void given_aFile_when_planned_then_nothingIsRegisteredAndEachRowIsSorted() throws Exception {
        foreignDepartments.add(8);
        CsvImportService.CsvImportPlan plan = service(r -> registeredAs("x")).plan(new ByteArrayInputStream(
                "1,1,,a,b,c,,e@x.org,\n1,8,,a,b,c,,e@x.org,\n".getBytes(StandardCharsets.UTF_8)));

        assertTrue(registered.isEmpty());
        assertFalse(plan.rejected());
        assertEquals(List.of(1), plan.accepted().stream().map(CsvImportService.CsvRow::line).toList());
        assertEquals(List.of("Line 2: You are not assigned to department 8"), plan.refused());
    }

    @Test
    void given_aRegistrationThatFailsUnexpectedly_when_imported_then_theLineGetsAGenericMessage() {
        CsvImportException e = assertThrows(CsvImportException.class, () -> importCsv(service(r -> {
            throw new IllegalStateException("could not execute statement; constraint fk_x");
        }), "# header\n1,1,,a,b,c,,e@x.org,\n"));
        assertEquals(List.of("Line 2: failed to process this record due to an unexpected error"), e.getLineErrors());
    }

    @Test
    void given_anEmptyUpload_when_imported_then_theResponseIsEmpty() throws Exception {
        CsvImportService service = service(r -> registeredAs("x"));
        AddResponse response = importCsv(service, "");
        assertTrue(response.getSubjects().isEmpty());
        assertTrue(response.getErrors().isEmpty());
        assertTrue(registered.isEmpty());
    }

    @Test
    void given_lineErrors_when_wrapped_then_theListIsACopy() {
        List<String> source = new ArrayList<>(List.of("Line 1: x"));
        CsvImportException e = new CsvImportException(source);
        source.add("Line 2: y");
        assertEquals(List.of("Line 1: x"), e.getLineErrors());
        assertEquals("Import completed with errors:\nLine 1: x", e.getMessage());
        AddResponse r = registeredAs("ok");
        assertSame(r.getSubjects().get(0), r.getSubjects().getFirst());
    }
}
