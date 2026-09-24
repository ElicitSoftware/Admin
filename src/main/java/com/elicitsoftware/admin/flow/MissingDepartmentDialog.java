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

import com.elicitsoftware.admin.i18n.Translations;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;

/**
 * The blocking notice shown when the signed-in console user has no department (UC-028).
 * <p>
 * Unlike {@link MissingSurveyNotice} and {@link DefaultAccountNotice}, which explain and never
 * block (BR-077, BR-087), this one is modal and cannot be dismissed (BR-110): a user with no
 * department can see nothing and register nobody, so there is no screen worth leaving usable
 * underneath. Two shapes, decided here so the wording and the question of who gets a remedy
 * stay in one place:
 * <ul>
 *   <li>an administrator is offered the Departments screen, where creating a department also
 *       assigns it to them (BR-113);</li>
 *   <li>a user is told to ask an administrator.</li>
 * </ul>
 * Both variants offer Logout, so nobody is ever trapped in a signed-in session they cannot
 * end (BR-112). The dialog takes no close button (nothing is added to its header), no Escape
 * and no click outside.
 */
final class MissingDepartmentDialog {

    /** Element id of the dialog, for tests and styling. */
    static final String DIALOG_ID = "missing-department-dialog";

    /** Element id of the administrator's remedy button. */
    static final String ADD_BUTTON_ID = "missing-department-add";

    /** Element id of the Logout button present in both variants. */
    static final String LOGOUT_BUTTON_ID = "missing-department-logout";

    private MissingDepartmentDialog() {
        // Static factory only
    }

    /** The variant for an administrator: explanation, Add a department, Logout. */
    static Dialog forAdministrator() {
        return build(true);
    }

    /** The variant for a user: explanation and Logout only (UC-028 A1). */
    static Dialog forUser() {
        return build(false);
    }

    private static Dialog build(boolean administrator) {
        Dialog dialog = new Dialog();
        dialog.setId(DIALOG_ID);
        dialog.setHeaderTitle(Translations.get("missingDepartmentDialog.title"));
        // STRICT: nothing behind the dialog -- drawer, banners, the routed view -- is reachable.
        dialog.setModality(ModalityMode.STRICT);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.setDraggable(false);
        dialog.setResizable(false);
        // alertdialog rather than dialog: the condition needs the user's attention before
        // anything else, and focus is trapped inside until an action is taken (NFR-016).
        dialog.setRole("alertdialog");
        dialog.add(new Paragraph(Translations.get(administrator
                ? "missingDepartmentDialog.admin.message" : "missingDepartmentDialog.user.message")));

        if (administrator) {
            Button add = new Button(Translations.get("missingDepartmentDialog.admin.action"), event -> {
                // Close first: the STRICT overlay would otherwise sit over the Departments screen.
                dialog.close();
                UI.getCurrent().navigate(DepartmentsView.class);
            });
            add.setId(ADD_BUTTON_ID);
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(add);
        }
        Button logout = new Button(Translations.get("missingDepartmentDialog.logout"), event -> {
            dialog.close();
            UI.getCurrent().navigate(LogoutView.class);
        });
        logout.setId(LOGOUT_BUTTON_ID);
        dialog.getFooter().add(logout);
        return dialog;
    }
}
