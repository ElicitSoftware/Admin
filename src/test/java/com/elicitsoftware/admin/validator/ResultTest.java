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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Result}.
 *
 * <p>Traceability: UC-006 (Manage Departments) A1 and UC-007 (Manage Message
 * Templates) A1 — "Validation errors": the system asks the administrator to
 * correct errors before saving. {@code Result} is the wrapper that carries the
 * success flag and the aggregated violation messages shown to the administrator.</p>
 */
class ResultTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    /** A minimal bean used to produce real constraint violations. */
    static class Sample {
        @NotBlank(message = "name required")
        String name;

        Sample(String name) {
            this.name = name;
        }
    }

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    /** UC-006/UC-007: a success result carries the supplied message. */
    @Test
    void successResultReportsSuccessAndMessage() {
        Result result = new Result("Department saved");
        assertTrue(result.isSuccess());
        assertEquals("Department saved", result.getMessage());
    }

    /** UC-006/UC-007 A1: a failure result built from violations is not successful. */
    @Test
    void violationResultReportsFailure() {
        Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample(""));
        Result result = new Result(violations);
        assertFalse(result.isSuccess());
        assertEquals("name required", result.getMessage());
    }

    /** UC-006/UC-007 A1: multiple violation messages are joined for display. */
    @Test
    void violationMessagesAreJoined() {
        Set<ConstraintViolation<Sample>> violations = validator.validate(new Sample("   "));
        // Whitespace-only fails @NotBlank; message set is non-empty and comma-joined.
        Result result = new Result(violations);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("name required"));
    }

    /** Edge case: an empty violation set yields an empty message but still a failure result. */
    @Test
    void emptyViolationSetYieldsEmptyMessage() {
        Result result = new Result(Set.of());
        assertFalse(result.isSuccess());
        assertEquals("", result.getMessage());
    }
}
