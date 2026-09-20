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

import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.upload.Upload;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.inject.spi.CDI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless UI test for {@link RespondentImportView}.
 *
 * <p>Traceability: UC-012 (Import Respondent Data). The view is obtained through CDI so its
 * constructor runs, matching {@code SearchViewTest}/{@code MainLayoutTest}'s pattern. Route
 * navigation is unavailable under {@code @QuarkusTest} (see the project's browserless-testing
 * notes), so {@link RespondentImportView#handleUpload(byte[])} - the package-private method the
 * {@code Upload}'s {@code UploadHandler} delegates to - is called directly rather than simulating
 * a real file upload.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RespondentImportViewTest extends QuarkusBrowserlessTest {

    private RespondentImportView view;

    @BeforeEach
    @TestSecurity(user = "import.admin", roles = {"elicit_admin"})
    void setUp() {
        view = CDI.current().select(RespondentImportView.class).get();
        UI.getCurrent().add(view);
    }

    /** UC-012: the view is restricted to elicit_admin. */
    @Test
    void viewRequiresAdminRole() {
        RolesAllowed rolesAllowed = RespondentImportView.class.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed, "RespondentImportView must declare @RolesAllowed");
        assertArrayEquals(new String[]{"elicit_admin"}, rolesAllowed.value());
    }

    /** UC-012: the view renders a header and an upload control accepting only .elicit files. */
    @Test
    @TestSecurity(user = "import.admin", roles = {"elicit_admin"})
    void rendersUploadControl() {
        assertTrue(find(H3.class, view).all().stream().anyMatch(h -> view.getTranslation("respondentImportView.title").equals(h.getText())));

        Upload upload = find(Upload.class, view).single();
        assertEquals("respondent-import-upload", upload.getId().orElse(null));
        assertEquals(List.of(".elicit"), upload.getAcceptedFileTypes());
        assertEquals(1, upload.getMaxFiles());
    }

    /** UC-012: a well-formed export file imports successfully and shows a success dialog. */
    @Test
    @TestSecurity(user = "import.admin", roles = {"elicit_admin"})
    @TestTransaction
    void wellFormedFileShowsSuccessDialog() {
        String content = "# ELICIT_EXPORT_V1\n\n"
                + "respondents: 1|import-view-test-token|0|2026-01-01T00:00:00-05:00|\n";

        view.handleUpload(content.getBytes(StandardCharsets.UTF_8));

        Dialog dialog = find(Dialog.class).single();
        assertTrue(dialog.getHeaderTitle().contains(view.getTranslation("respondentImportView.importSuccessful")));
    }

    /** UC-012: a file missing the format-version header fails without throwing, and shows an error dialog. */
    @Test
    @TestSecurity(user = "import.admin", roles = {"elicit_admin"})
    @TestTransaction
    void malformedFileShowsErrorDialog() {
        String content = "respondents: 1|tok|0|2026-01-01T00:00:00-05:00|\n";

        view.handleUpload(content.getBytes(StandardCharsets.UTF_8));

        Dialog dialog = find(Dialog.class).single();
        assertTrue(dialog.getHeaderTitle().contains(view.getTranslation("respondentImportView.importFailed")));
    }
}
