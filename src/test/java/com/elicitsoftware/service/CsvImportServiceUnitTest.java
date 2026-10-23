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

    private CsvImportService service(Function<AddRequest, AddResponse> answer) {
        AccessCodeService accessCodes = new AccessCodeService() {
            @Override
            public AddResponse putSubject(AddRequest request) {
                registered.add(request);
                return answer.apply(request);
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
                "# departmentId,firstName,...\n\n 1 , Jane , Doe ,, 1980-02-03 , j@x.org , 555 , X1 \n"
                        + "2,\"Doe, Jr\",Smith,M,02/03/1981,a@x.org\n");

        assertEquals(2, registered.size());
        AddRequest first = registered.get(0);
        assertEquals(1, first.departmentId);
        assertEquals(1, first.surveyId);
        assertEquals("Jane", first.firstName);
        assertEquals("Doe", first.lastName);
        assertEquals("", first.middleName);
        assertEquals(LocalDate.of(1980, 2, 3), first.dob);
        assertEquals("j@x.org", first.email);
        assertEquals("555", first.phone);
        assertEquals("X1", first.xid);

        AddRequest second = registered.get(1);
        assertEquals(2, second.departmentId);
        assertEquals("Doe, Jr", second.firstName, "a quoted field keeps its comma");
        assertEquals("M", second.middleName);
        assertEquals(LocalDate.of(1981, 2, 3), second.dob);
        assertNull(second.phone);
        assertNull(second.xid);

        assertEquals(2, response.getSubjects().size());
        assertEquals("New Subject X1", response.getSubjects().get(0).getImportStatus());
        assertEquals("New Subject null", response.getSubjects().get(1).getImportStatus());
        assertTrue(response.getErrors().isEmpty());
    }

    @Test
    void given_aLineWithoutDateOfBirth_when_imported_then_theDateIsLeftEmpty() throws Exception {
        importCsv(service(r -> registeredAs("ok")), "3,A,B,C,,e@x.org,,\n");
        assertNull(registered.get(0).dob);
        assertEquals("", registered.get(0).phone);
        assertEquals("", registered.get(0).xid);

        importCsv(service(r -> registeredAs("ok")), "3,A,B,C,,e@x.org,555\n");
        assertEquals("555", registered.get(1).phone);
        assertNull(registered.get(1).xid, "seven fields carry a phone but no external id");
    }

    @Test
    void given_aRefusedRegistration_when_imported_then_itsErrorIsCarriedOnTheResponse() throws Exception {
        AddResponse refused = new AddResponse();
        refused.setError("invalid departmentid");
        refused.setError("second");
        AddResponse response = importCsv(service(r -> refused), "9,A,B,C,,e@x.org\n");
        assertEquals(List.of("invalid departmentid"), response.getErrors());
        assertTrue(response.getSubjects().isEmpty());
    }

    @Test
    void given_badLines_when_imported_then_eachIsReportedWithItsLineNumber() {
        List<String> errors = lineErrors("1,a,b\n"
                + "x,a,b,c,,e@x.org\n"
                + "1,a,b,c,31/31/2020,e@x.org\n"
                + "1, ,b,c,,e@x.org\n"
                + "1,a, ,c,,e@x.org\n"
                + "1,a,b,c,, \n"
                + "1,a,b,c,,e@x.org\n");
        assertEquals(List.of(
                "Line 1: Invalid CSV format. Expected at least 6 fields: departmentId,firstName,lastName,middleName,dob,email,phone,xid",
                "Line 2: Invalid department ID format",
                "Line 3: Invalid date format. Use yyyy-MM-dd or MM/dd/yyyy",
                "Line 4: First name is required",
                "Line 5: Last name is required",
                "Line 6: Email is required"), errors);
        assertEquals(1, registered.size(), "only the valid line reached registration");
    }

    @Test
    void given_aRegistrationThatFailsUnexpectedly_when_imported_then_theLineGetsAGenericMessage() {
        CsvImportException e = assertThrows(CsvImportException.class, () -> importCsv(service(r -> {
            throw new IllegalStateException("could not execute statement; constraint fk_x");
        }), "# header\n1,a,b,c,,e@x.org\n"));
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
