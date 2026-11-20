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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-028: Require a Department Assignment — the blocking dialog built by
 * {@link MissingDepartmentDialog}, in a plain browserless UI. Covers the strict, undismissable
 * modality (BR-110), the administrator's remedy (BR-111), the always-present logout (BR-112) and
 * the manual link that appears only when the manual is packaged (UC-029).
 */
class MissingDepartmentDialogUnitTest extends PlainSmallViewTestSupport {

    private static List<Component> footer(Dialog dialog) {
        return dialog.getFooter().getElement().getChildren()
                .map(e -> e.getComponent().orElseThrow()).toList();
    }

    private static Optional<Component> footerById(Dialog dialog, String id) {
        return footer(dialog).stream().filter(c -> c.getId().filter(id::equals).isPresent()).findFirst();
    }

    private Dialog open(Dialog dialog) {
        dialog.open();
        roundTrip();
        return dialog;
    }

    @Test
    void given_administrator_when_built_then_dialogBlocksAndOffersTheDepartmentsRemedy() {
        Dialog dialog = MissingDepartmentDialog.forAdministrator(false);

        assertEquals(MissingDepartmentDialog.DIALOG_ID, dialog.getId().orElseThrow());
        assertEquals(t("missingDepartmentDialog.title"), dialog.getHeaderTitle());
        assertEquals(ModalityMode.STRICT, dialog.getModality());
        assertFalse(dialog.isCloseOnEsc());
        assertFalse(dialog.isCloseOnOutsideClick());
        assertFalse(dialog.isDraggable());
        assertFalse(dialog.isResizable());
        assertEquals(Optional.of("alertdialog"), dialog.getAriaRole());
        Paragraph message = (Paragraph) dialog.getChildren().findFirst().orElseThrow();
        assertEquals(t("missingDepartmentDialog.admin.message"), message.getText());

        List<Component> footer = footer(dialog);
        assertEquals(2, footer.size(), "no manual link: add and logout only");
        Button add = (Button) footer.get(0);
        assertEquals(MissingDepartmentDialog.ADD_BUTTON_ID, add.getId().orElseThrow());
        assertEquals(t("missingDepartmentDialog.admin.action"), add.getText());
        assertTrue(add.getThemeNames().contains(ButtonVariant.LUMO_PRIMARY.getVariantName()));
        Button logout = (Button) footer.get(1);
        assertEquals(MissingDepartmentDialog.LOGOUT_BUTTON_ID, logout.getId().orElseThrow());
        assertEquals(t("missingDepartmentDialog.logout"), logout.getText());
        assertFalse(footerById(dialog, MissingDepartmentDialog.MANUAL_LINK_ID).isPresent());
    }

    @Test
    void given_user_when_built_then_onlyLogoutIsOfferedWithTheUserMessage() {
        Dialog dialog = MissingDepartmentDialog.forUser(false);

        Paragraph message = (Paragraph) dialog.getChildren().findFirst().orElseThrow();
        assertEquals(t("missingDepartmentDialog.user.message"), message.getText());
        List<Component> footer = footer(dialog);
        assertEquals(1, footer.size());
        assertEquals(MissingDepartmentDialog.LOGOUT_BUTTON_ID, footer.get(0).getId().orElseThrow());
        assertFalse(footerById(dialog, MissingDepartmentDialog.ADD_BUTTON_ID).isPresent());
    }

    @Test
    void given_manualAvailable_when_built_then_manualLinkLeadsTheFooterAndOpensOutsideTheRouter() {
        Dialog dialog = MissingDepartmentDialog.forUser(true);

        List<Component> footer = footer(dialog);
        assertEquals(2, footer.size());
        Anchor manual = (Anchor) footer.get(0);
        assertEquals(MissingDepartmentDialog.MANUAL_LINK_ID, manual.getId().orElseThrow());
        assertEquals("/api/manual", manual.getHref());
        assertEquals(t("missingDepartmentDialog.manual"), manual.getText());
        assertTrue(manual.isRouterIgnore());
        assertEquals(Optional.of(AnchorTarget.BLANK.getValue()), manual.getTarget());
        assertTrue(manual.hasClassName("dialog-manual-link"));
        assertEquals("vaadin-icon", manual.getElement().getChild(0).getTag(), "the icon comes first");
        assertEquals(MissingDepartmentDialog.LOGOUT_BUTTON_ID, footer.get(1).getId().orElseThrow());
    }

    @Test
    void given_administratorAndManual_when_built_then_footerReadsManualAddLogout() {
        List<Component> footer = footer(MissingDepartmentDialog.forAdministrator(true));

        assertEquals(List.of(MissingDepartmentDialog.MANUAL_LINK_ID, MissingDepartmentDialog.ADD_BUTTON_ID,
                        MissingDepartmentDialog.LOGOUT_BUTTON_ID),
                footer.stream().map(c -> c.getId().orElseThrow()).toList());
    }

    @Test
    void given_openAdministratorDialog_when_addClicked_then_dialogClosesAndDepartmentsIsEntered() {
        Dialog dialog = open(MissingDepartmentDialog.forAdministrator(false));
        Button add = (Button) footerById(dialog, MissingDepartmentDialog.ADD_BUTTON_ID).orElseThrow();

        try {
            test(add).click();
        } catch (RuntimeException expected) {
            // The real Departments view queries the database when built; the navigation was asked for.
        }

        assertFalse(dialog.isOpened(), "the strict overlay must not sit over the Departments screen");
        assertEquals(List.of("departments"), attemptedPaths);
    }

    @Test
    void given_openUserDialog_when_logoutClicked_then_dialogClosesAndLogoutIsEntered() {
        Dialog dialog = open(MissingDepartmentDialog.forUser(false));
        Button logout = (Button) footerById(dialog, MissingDepartmentDialog.LOGOUT_BUTTON_ID).orElseThrow();

        test(logout).click();

        assertFalse(dialog.isOpened());
        assertEquals(List.of("logout"), attemptedPaths);
    }
}
