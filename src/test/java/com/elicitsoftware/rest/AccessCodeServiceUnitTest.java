package com.elicitsoftware.rest;

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

import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.security.DepartmentScope;
import com.elicitsoftware.service.CsvImportException;
import com.elicitsoftware.service.CsvImportService;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-015: Generate Survey Access Code and UC-010: Register Subjects via Integration API — the
 * parts of {@link AccessCodeService} that need no database: the access code alphabet, the CSV
 * upload hand-off, the per-request isolation of a batch registration, and the department a caller
 * may register into.
 */
class AccessCodeServiceUnitTest {

    @Test
    void given_noUploadedFile_when_aCsvIsImported_then_theResponseCarriesTheError() {
        AddResponse response = forImportClient().importCsv(new MultipartBody());
        assertEquals(List.of("No CSV file provided in the 'file' field"), response.getErrors());
    }

    @Test
    void given_anUploadedFile_when_aCsvIsImported_then_theCsvServiceAnswers() {
        AddResponse expected = new AddResponse();
        InputStream file = new ByteArrayInputStream(new byte[0]);
        InputStream[] received = new InputStream[1];
        AccessCodeService service = forImportClient();
        service.csvImportService = new CsvImportService(null) {
            @Override
            public AddResponse importSubjects(InputStream csvInputStream) {
                received[0] = csvInputStream;
                return expected;
            }
        };
        MultipartBody body = new MultipartBody();
        body.file = file;

        assertSame(expected, service.importCsv(body));
        assertSame(file, received[0]);
    }

    @Test
    void given_aCsvImportWithRejectedLines_when_imported_then_theLinesAreTheError() {
        AccessCodeService service = forImportClient();
        service.csvImportService = new CsvImportService(null) {
            @Override
            public AddResponse importSubjects(InputStream csvInputStream) throws Exception {
                throw new CsvImportException(List.of("Line 1: Email is required"));
            }
        };
        MultipartBody body = new MultipartBody();
        body.file = new ByteArrayInputStream(new byte[0]);
        assertEquals(List.of("Import completed with errors:\nLine 1: Email is required"), service.importCsv(body).getErrors());
    }

    @Test
    void given_aCsvImportThatFailsUnexpectedly_when_imported_then_theErrorIsAFixedPhraseWithAReference() {
        AccessCodeService service = forImportClient();
        service.csvImportService = new CsvImportService(null) {
            @Override
            public AddResponse importSubjects(InputStream csvInputStream) throws Exception {
                throw new IllegalStateException("could not execute statement; SQL [insert into survey.subjects]; constraint fk_x");
            }
        };
        MultipartBody body = new MultipartBody();
        body.file = new ByteArrayInputStream(new byte[0]);

        List<String> errors = service.importCsv(body).getErrors();

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).matches("Error \\(ref [0-9a-f]{8}\\)"), "BR-139: " + errors.get(0));
    }

    @Test
    void given_anUnexpectedFailure_when_reported_then_nothingOfItReachesTheCaller() {
        String phrase = AccessCodeService.logged(new IllegalStateException("SQL constraint uk_subject violated at db:5432"));

        assertTrue(phrase.matches("Error \\(ref [0-9a-f]{8}\\)"), phrase);
        assertFalse(AccessCodeService.logged(new IllegalStateException("x")).equals(phrase), "each failure has its own reference");
    }

    @Test
    void given_theAccessCodeAlphabet_when_inspected_then_eachOfIts46CharactersAppearsOnce() {
        String alphabet = AccessCodeService.ACCESS_CODE_ALPHABET;

        assertEquals(46, alphabet.length());
        assertEquals(46, alphabet.chars().distinct().count(), "BR-050: no character is more likely than another");
        assertEquals("BCDFGHJKLMNPQRSTVWXZbcdfghjkmnpqrstvwxz2456789", alphabet);
        for (char ambiguous : "0O1lIAEUaeiou3".toCharArray()) {
            assertEquals(-1, alphabet.indexOf(ambiguous), "BR-050: " + ambiguous);
        }
    }

    /** A stand-in for the caller's console record: assigned to every department but those listed. */
    private static DepartmentScope scopeWithout(long... foreign) {
        return new DepartmentScope() {
            @Override
            public boolean permits(long departmentId) {
                for (long id : foreign) {
                    if (id == departmentId) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void require(long departmentId) {
                if (!permits(departmentId)) {
                    throw new ForbiddenException();
                }
            }

            @Override
            public void requireImportClient() {
                // The caller is an import client (UC-010 BR-170).
            }
        };
    }

    /** A stand-in for a caller that is not an import client: every request is refused (BR-170). */
    private static DepartmentScope notAnImportClient() {
        return new DepartmentScope() {
            @Override
            public void requireImportClient() {
                throw new ForbiddenException();
            }
        };
    }

    /** The service as an import client assigned to every department calls it. */
    private static AccessCodeService forImportClient() {
        AccessCodeService service = new AccessCodeService();
        service.departmentScope = scopeWithout();
        return service;
    }

    @Test
    void given_aCallerThatIsNotAnImportClient_when_anyOfTheThreeEndpointsIsCalled_then_theWholeRequestIsForbidden() {
        AccessCodeService service = new AccessCodeService();
        service.departmentScope = notAnImportClient();
        AddRequest request = new AddRequest();
        request.xid = "X1";
        MultipartBody body = new MultipartBody();
        body.file = new ByteArrayInputStream(new byte[0]);

        assertThrows(ForbiddenException.class, () -> service.putSubject(request), "BR-170");
        assertThrows(ForbiddenException.class, () -> service.putSubjects(List.of(request)), "BR-170");
        // Not turned into an answer by the CSV endpoint's own error handling.
        assertThrows(ForbiddenException.class, () -> service.importCsv(body), "BR-170");
    }

    @Test
    void given_aDepartmentTheCallerIsNotAssignedTo_when_oneSubjectIsRegistered_then_theRequestIsForbiddenBeforeAnythingIsLookedUp() {
        AccessCodeService service = new AccessCodeService();
        service.departmentScope = scopeWithout(8);
        AddRequest request = new AddRequest();
        request.xid = "X1";
        request.departmentId = 8;

        // Nothing but the department rule can answer here: there is no database behind the service.
        assertThrows(ForbiddenException.class, () -> service.putSubject(request), "BR-137");
    }

    @Test
    void given_theCallersDepartments_when_asked_then_theServiceAnswersFromTheDepartmentRule() {
        AccessCodeService service = new AccessCodeService();
        service.departmentScope = scopeWithout(8);

        assertFalse(service.permitsDepartment(8));
        assertTrue(service.permitsDepartment(3));
    }

    @Test
    void given_aBatchWhoseTransactionsCannotRun_when_registered_then_eachRequestReportsItsOwnFailure() {
        AddRequest first = new AddRequest();
        first.xid = "X1";
        AddRequest second = new AddRequest();
        second.xid = "X2";

        AddResponse response = forImportClient().putSubjects(List.of(first, second));

        assertEquals(2, response.getSubjects().size());
        assertTrue(response.getSubjects().get(0).getImportStatus().matches("Unexpected error processing X1 Error \\(ref [0-9a-f]{8}\\)"),
                "BR-139: " + response.getSubjects().get(0).getImportStatus());
        assertTrue(response.getSubjects().get(1).getImportStatus().matches("Unexpected error processing X2 Error \\(ref [0-9a-f]{8}\\)"));
        assertTrue(response.getErrors().isEmpty());
    }
}
