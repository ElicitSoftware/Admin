package com.elicitsoftware.admin.validator;

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

import com.elicitsoftware.model.Subject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RespondentValidator}.
 *
 * <p>Traceability: UC-003 (Register or Update a Subject), step 5 — the system
 * validates the entered values. A subject must have at least one usable contact
 * method (email or phone) so invitation and reminder messages (UC-004) can be
 * delivered. These tests exercise that rule directly against the constraint
 * validator, without needing the Bean Validation runtime.</p>
 */
class RespondentValidatorTest {

    private final RespondentValidator validator = new RespondentValidator();

    private Subject subjectWith(String email, String phone) {
        Subject subject = new Subject();
        subject.setEmail(email);
        subject.setPhone(phone);
        return subject;
    }

    /** UC-003: email only is a valid contact method. */
    @Test
    void emailOnlyIsValid() {
        assertTrue(validator.isValid(subjectWith("user@example.com", null), null));
    }

    /** UC-003: phone only is a valid contact method. */
    @Test
    void phoneOnlyIsValid() {
        assertTrue(validator.isValid(subjectWith(null, "555-1234"), null));
    }

    /** UC-003: having both email and phone is valid. */
    @Test
    void bothContactMethodsAreValid() {
        assertTrue(validator.isValid(subjectWith("user@example.com", "555-1234"), null));
    }

    /** UC-003: neither contact method present fails validation. */
    @Test
    void neitherContactMethodIsInvalid() {
        assertFalse(validator.isValid(subjectWith(null, null), null));
    }

    /** UC-003: blank (whitespace-only) values do not count as a contact method. */
    @Test
    void blankContactMethodsAreInvalid() {
        assertFalse(validator.isValid(subjectWith("   ", "  "), null));
    }
}
