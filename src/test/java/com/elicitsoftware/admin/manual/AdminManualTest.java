package com.elicitsoftware.admin.manual;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-029: the manual ships with the application (BR-004). The test classpath carries a stand-in
 * PDF at {@code src/test/resources/manual/}, so these tests exercise the build that has one;
 * {@link MissingManualTest} covers the build that does not (UC-029 A1).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AdminManualTest {

    @Inject
    AdminManual manual;

    /** UC-029: a build that carries the manual offers it. */
    @Test
    void aBuildThatCarriesTheManualOffersIt() {
        assertTrue(manual.isAvailable(), "the packaged manual should be found on the classpath");
    }

    /** UC-029 step 2: what is opened is a printable document, not a placeholder. */
    @Test
    void theManualOpensAsAPdf() throws Exception {
        try (InputStream pdf = manual.open()) {
            assertNotNull(pdf, "isAvailable() and open() must agree");
            byte[] header = pdf.readNBytes(5);
            assertEquals("%PDF-", new String(header), "the packaged manual is a PDF");
        }
    }

    /** UC-029 A2: the name the reader's saved or printed copy carries. */
    @Test
    void theFileNameIsWhatTheReaderSaves() {
        assertEquals("elicit-admin-manual.pdf", AdminManual.FILE_NAME);
    }
}
