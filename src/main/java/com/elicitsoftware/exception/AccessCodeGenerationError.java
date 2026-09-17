package com.elicitsoftware.exception;

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

/**
 * Thrown when Admin cannot register a subject's survey access.
 * <p>
 * Raised by {@link com.elicitsoftware.rest.AccessCodeService#generateAccessCode(int)} when it
 * cannot produce an access code that is unique within the survey, and by
 * {@link com.elicitsoftware.model.Message#createMessagesForSubject} when the subject's
 * department or its invitation template IDs are invalid.
 * <p>
 * This is an unchecked exception. Callers at the UI and REST boundaries catch it and report a
 * short message; the message must not contain an access code.
 *
 * @since 1.0
 */
public class AccessCodeGenerationError extends RuntimeException {

    /** Serial version UID for serialization compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a description of the failure.
     *
     * @param message the detail message; must not contain an access code
     */
    public AccessCodeGenerationError(String message) {
        super(message);
    }
}
