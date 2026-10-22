package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.User;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.streams.DownloadResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-031: the Participation page renders for a console user, offers only the user's departments
 * (BR-120), refuses a reversed date range (A3) and exports the figures on screen as CSV (BR-126).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ParticipationViewTest extends QuarkusBrowserlessTest {

    private ParticipationView view;

    @BeforeEach
    @TestSecurity(user = "participation.tester", roles = {"elicit_user"})
    void setUp() {
        User user = new User();
        user.setId(1);
        user.setUsername("participation.tester");
        user.setActive(true);
        Department department = new Department();
        department.id = 1;
        department.name = "Participation Dept";
        Set<Department> departments = new HashSet<>();
        departments.add(department);
        user.setDepartments(departments);
        VaadinSession.getCurrent().setAttribute("user", user);
        view = CDI.current().select(ParticipationView.class).get();
        UI.getCurrent().add(view);
    }

    /** UC-031 steps 2 to 5: the filter and the four sections are drawn for the seeded survey. */
    @Test
    @TestSecurity(user = "participation.tester", roles = {"elicit_user"})
    void pageRendersTheSectionsForAConsoleUser() {
        assertNotNull(view.surveyPicker.getValue(), "the first installed survey is chosen");
        List<String> headings = find(H4.class, view.results).all().stream().map(H4::getText).toList();
        assertEquals(List.of("Participation", "By department", "By week", "Progress", "Marked questions"), headings);
        assertNotNull(view.shown, "the figures on screen are kept for the downloads");
    }

    /** UC-031 BR-120: only "All departments" and the user's own departments are offered. */
    @Test
    @TestSecurity(user = "participation.tester", roles = {"elicit_user"})
    void departmentFilterOffersOnlyTheUsersDepartments() {
        assertEquals(List.of("Participation Dept"), view.chosenDepartments().stream().map(d -> d.name).toList());
        assertEquals(List.of(1L), view.filter().departmentIds());
    }

    /** UC-031 BR-120: clearing the department filter falls back to all departments and recomputes. */
    @Test
    @TestSecurity(user = "participation.tester", roles = {"elicit_user"})
    void clearingTheDepartmentsRecomputesForAll() {
        Department mine = view.chosenDepartments().get(0);
        view.departmentPicker.setValue(Set.of(mine));
        Object narrowed = view.shown;
        view.departmentPicker.setValue(Set.of());
        assertEquals(1, view.departmentPicker.getValue().size());
        assertEquals(-1L, view.departmentPicker.getValue().iterator().next().id);
        assertFalse(narrowed == view.shown, "the page is recomputed for all departments");
    }

    /** UC-031 A3: a reversed range marks the field and recomputes nothing. */
    @Test
    @TestSecurity(user = "participation.tester", roles = {"elicit_user"})
    void reversedRangeIsRefused() {
        Object before = view.shown;
        view.fromPicker.setValue(LocalDate.of(2026, 2, 1));
        Object afterFrom = view.shown;
        view.toPicker.setValue(LocalDate.of(2026, 1, 1));
        assertTrue(view.toPicker.isInvalid());
        assertEquals(afterFrom, view.shown, "nothing is recomputed for a reversed range");
        assertFalse(before == null);
    }

    /** UC-031 BR-126: the CSV download is the figures on screen, UTF-8 with a byte order mark. */
    @Test
    @TestSecurity(user = "participation.tester", roles = {"elicit_user"})
    void csvDownloadCarriesTheFilterAndTheTables() throws IOException {
        DownloadResponse response = view.csvDownload();
        assertEquals("text/csv", response.getContentType());
        assertTrue(response.getFileName().startsWith("participation-") && response.getFileName().endsWith(".csv"));
        String csv = new String(response.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿Survey,"), csv);
        assertTrue(csv.contains("Departments,All departments\r\n"), csv);
        assertTrue(csv.contains("\r\nBy week\r\n"), csv);
    }
}
