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

import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.test.ScriptedEntityManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: Generate and Download a Subject Report — the language a report is requested in and the
 * error page that stands in for a report service that cannot be called (BR-023 the report URL).
 */
class ReportingServiceUnitTest {

    private ScriptedEntityManager db;

    private ReportingService service(Function<ScriptedEntityManager.Call, Object> script) {
        db = new ScriptedEntityManager(script);
        ReportingService service = new ReportingService();
        service.em = db.entityManager();
        service.surveyUrl = "http://survey:8080";
        return service;
    }

    private static ReportResponse callReport(ReportingService service, String name, String url) throws Exception {
        ReportDefinition rpt = new ReportDefinition();
        rpt.name = name;
        rpt.url = url;
        Method m = ReportingService.class.getDeclaredMethod("callReport", ReportDefinition.class, int.class);
        m.setAccessible(true);
        return (ReportResponse) m.invoke(service, rpt, 17);
    }

    @Test
    void given_answersWithALanguage_when_theAnsweringLanguageIsAsked_then_theLatestIsTaken() {
        ReportingService service = service(call -> List.of("es-419"));
        assertEquals("es-419", service.answeringLanguage(17));
        assertEquals(17, db.calls().get(0).param(1));
        assertTrue(db.calls().get(0).is("display_language IS NOT NULL"));
        assertTrue(db.calls().get(0).is("ORDER BY saved_dt DESC NULLS LAST, id DESC LIMIT 1"));
    }

    @Test
    void given_noAnswerWithALanguage_when_theAnsweringLanguageIsAsked_then_thereIsNone() {
        assertNull(service(call -> List.of()).answeringLanguage(17));
    }

    @Test
    void given_aReportThatCannotBePrepared_when_called_then_anErrorPageStandsInForTheReport() throws Exception {
        String reason = "connection to the database lost";
        ReportResponse response = callReport(service(call -> new IllegalStateException(reason)), "Pedigree", "/api/x");

        assertEquals("Error - Pedigree", response.title);
        assertEquals("<div style=\"color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;\">"
                + "<h3>Report Generation Error</h3><p><strong>Service:</strong> Pedigree</p><p><strong>Error:</strong> "
                + reason + "</p></div>", response.innerHTML);
        assertEquals("Error - Pedigree", response.pdf.title);
        assertEquals(1, response.pdf.content.length);
        assertEquals("Report Generation Error - Service: Pedigree - Error: " + reason, response.pdf.content[0].text);
    }

    @Test
    void given_aFailureWithoutMessage_when_called_then_theErrorIsUnknown() throws Exception {
        ReportResponse response = callReport(service(call -> new IllegalStateException()), "Pedigree", "/api/x");
        assertEquals("Report Generation Error - Service: Pedigree - Error: Unknown error", response.pdf.content[0].text);
    }

    @Test
    void given_aFailureMessageWithLineBreaks_when_called_then_thePdfTextIsOneLine() throws Exception {
        ReportResponse response = callReport(service(call -> new IllegalStateException(" db\ndown\tnow\r ")), "Pedigree", "/api/x");
        assertEquals("Report Generation Error - Service: Pedigree - Error: db down now", response.pdf.content[0].text);
        assertTrue(response.innerHTML.contains("<strong>Error:</strong>  db\ndown\tnow\r </p>"));
    }

    @Test
    void given_aReportRequest_when_built_then_itCarriesTheRespondentAndLanguage() {
        ReportRequest request = new ReportRequest(17, "ar");
        assertEquals(17, request.getId());
        assertEquals("ar", request.getLanguage());
        request.setId(18);
        request.setLanguage(null);
        assertEquals(18, request.getId());
        assertNull(request.getLanguage());
        assertNull(new ReportRequest(3).getLanguage());
    }
}
