package com.elicitsoftware.response;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ListResponse}: constructor and accessor round-tripping.
 *
 * <p>Traceability: UC-002 (Search and Monitor Subject Progress) — a paginated
 * subjects-plus-total-count response shape for list-style API responses.</p>
 */
class ListResponseTest {

    @Test
    void constructorSetsRespondentsAndLength() {
        Subject subject = new Subject();
        subject.setId(1L);
        List<Subject> respondents = List.of(subject);

        ListResponse response = new ListResponse(respondents, 42L);

        assertEquals(respondents, response.getRespondents());
        assertEquals(42L, response.getLength());
    }

    @Test
    void settersUpdateFields() {
        ListResponse response = new ListResponse(null, 0L);
        assertNull(response.getRespondents());

        List<Subject> respondents = List.of(new Subject());
        response.setRespondents(respondents);
        response.setLength(7L);

        assertEquals(respondents, response.getRespondents());
        assertEquals(7L, response.getLength());
    }
}
