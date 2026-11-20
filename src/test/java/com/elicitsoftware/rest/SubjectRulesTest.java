package com.elicitsoftware.rest;

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
import com.elicitsoftware.request.AddRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-010 BR-188, UC-003 BR-009 and BR-010: the faults {@link AccessCodeService#subjectRuleViolations}
 * finds in a record, one per field, in the Register page's words where it has them.
 */
class SubjectRulesTest {

    private final AccessCodeService service = new AccessCodeService();

    private static AddRequest record() {
        AddRequest request = new AddRequest();
        request.surveyId = 1;
        request.departmentId = 1;
        request.xid = "RULES-1";
        request.firstName = "Ann";
        request.lastName = "Lee";
        request.email = "ann.lee@example.org";
        request.phone = "555-010-0100";
        return request;
    }

    @Test
    void given_aRecordThatKeepsEveryRule_when_checked_then_itHasNoFault() {
        assertEquals(List.of(), service.subjectRuleViolations(record()));
    }

    @Test
    void given_noPhone_when_checked_then_itHasNoFault_becauseEmailIsTheOneRequiredContact() {
        AddRequest request = record();
        request.phone = null;
        assertEquals(List.of(), service.subjectRuleViolations(request));
    }

    @Test
    void given_aBlankPhone_when_checked_then_itIsNoPhoneAndNotAMalformedOne() {
        AddRequest request = record();
        request.phone = "";
        assertEquals(List.of(), service.subjectRuleViolations(request));
    }

    @Test
    void given_noEmail_when_checked_then_theEmailIsRequired_evenWithAPhone() {
        AddRequest request = record();
        request.email = " ";
        assertEquals(List.of("Email is required"), service.subjectRuleViolations(request));
    }

    @Test
    void given_aMalformedEmail_when_checked_then_theEmailIsInvalid() {
        AddRequest request = record();
        request.email = "ann.lee";
        assertEquals(List.of("Enter a valid email address"), service.subjectRuleViolations(request));
    }

    @Test
    void given_aMalformedPhone_when_checked_then_thePhoneFormatIsNamed() {
        AddRequest request = record();
        request.phone = "5550100100";
        assertEquals(List.of("Phone must be ###-###-####"), service.subjectRuleViolations(request));
    }

    @Test
    void given_aFutureDateOfBirth_when_checked_then_itMustBeInThePast() {
        AddRequest request = record();
        request.dob = LocalDate.now().plusDays(1);
        assertEquals(List.of("Date of birth must be in the past"), service.subjectRuleViolations(request));
    }

    /** UC-003 BR-189: the limit is named in the Register page's words, not the constraint's. */
    @Test
    void given_anOverlongName_when_checked_then_itsLengthIsNamed() {
        AddRequest request = record();
        request.lastName = "L".repeat(51);
        assertEquals(List.of("Last name can be at most 50 characters"), service.subjectRuleViolations(request));
    }

    @Test
    void given_aNameOfExactlyTheLimit_when_checked_then_itHasNoFault() {
        AddRequest request = record();
        request.firstName = "F".repeat(50);
        request.middleName = "M".repeat(50);
        assertEquals(List.of(), service.subjectRuleViolations(request));
    }

    @Test
    void given_anOverlongMiddleName_when_checked_then_itsLengthIsNamed() {
        AddRequest request = record();
        request.middleName = "M".repeat(51);
        assertEquals(List.of("Middle name can be at most 50 characters"), service.subjectRuleViolations(request));
    }

    @Test
    void given_anOverlongEmail_when_checked_then_itsLengthIsNamed() {
        AddRequest request = record();
        request.email = overlongEmail();
        assertEquals(List.of("Email address can be at most 255 characters"), service.subjectRuleViolations(request));
    }

    /** A well-formed address of 256 characters: a 60-character local part and three 63-character labels. */
    static String overlongEmail() {
        String label = "d".repeat(63);
        return "a".repeat(60) + "@" + label + "." + label + "." + label + ".org";
    }

    @Test
    void given_severalFaults_when_checked_then_eachFieldIsNamedOnceInTheRecordsOrder() {
        AddRequest request = record();
        request.firstName = "";
        request.email = "not-an-address";
        request.phone = "x";
        assertEquals(List.of("First name is required", "Enter a valid email address", "Phone must be ###-###-####"),
                service.subjectRuleViolations(request));
    }
}
