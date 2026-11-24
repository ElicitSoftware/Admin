package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.report.ReportResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-005: Generate and Download a Subject Report — BR-136, a print holds only its own
 * respondent's reports. The service is one bean shared by every session, so two prints running at
 * the same moment go through the same instance.
 */
class ReportingServiceConcurrencyTest {

    private static final int ROUNDS = 50;

    /** A report service that takes a moment to answer and names the respondent it was asked for. */
    private static final class SlowReports extends ReportingService {
        @Override
        ReportResponse callReport(ReportDefinition rpt, int respondentId) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ReportResponse response = new ReportResponse();
            response.title = rpt.name + " for " + respondentId;
            return response;
        }
    }

    private static List<ReportDefinition> definitions(String... names) {
        List<ReportDefinition> definitions = new ArrayList<>();
        for (String name : names) {
            ReportDefinition definition = new ReportDefinition();
            definition.name = name;
            definitions.add(definition);
        }
        return definitions;
    }

    @Test
    void given_twoPrintsAtTheSameMoment_when_theReportsAreCollected_then_eachHoldsOnlyItsOwnRespondent() throws Exception {
        ReportingService service = new SlowReports();
        List<ReportDefinition> definitions = definitions("Summary", "Pedigree", "Risk");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                CyclicBarrier together = new CyclicBarrier(2);
                Future<ArrayList<ReportResponse>> first = pool.submit(() -> {
                    together.await(5, TimeUnit.SECONDS);
                    return service.collectReports(definitions, 17);
                });
                Future<ArrayList<ReportResponse>> second = pool.submit(() -> {
                    together.await(5, TimeUnit.SECONDS);
                    return service.collectReports(definitions, 42);
                });

                assertEquals(List.of("Summary for 17", "Pedigree for 17", "Risk for 17"), titles(first.get(10, TimeUnit.SECONDS)));
                assertEquals(List.of("Summary for 42", "Pedigree for 42", "Risk for 42"), titles(second.get(10, TimeUnit.SECONDS)));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void given_aSurveyWithNoReports_when_theReportsAreCollected_then_theListIsEmpty() {
        assertEquals(List.of(), new SlowReports().collectReports(List.of(), 17));
    }

    private static List<String> titles(List<ReportResponse> responses) {
        return responses.stream().map(response -> response.title).toList();
    }
}
