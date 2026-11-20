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
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-029 (Consult the Administrator's Manual), A7: the blocking no-department notice of UC-028
 * offers the manual alongside its own action, because the manual is what describes the very
 * procedure the notice is demanding -- and a reader held by that modal can reach nothing else.
 * <p>
 * A build that carries no manual simply does not offer it (UC-029 A1), so the notice is asserted
 * both ways. The dialog is built through its factory rather than driven through
 * {@link MainLayout}, which keeps the assertions on the notice itself and off the packaging:
 * whether this image carries a manual is {@code AdminManual}'s question and is covered by its
 * own tests.
 * <p>
 * The dialog's own id is not usable for locating it -- {@code setId} on a {@link Dialog} lands on
 * the hidden host element, not the overlay -- so the link is located by its own id through the
 * browserless {@code find} helper, exactly as {@code MissingDepartmentDialogTest} locates its
 * buttons. {@code Dialog.DialogFooter#getChildren()} is not an alternative: it throws
 * {@code UnsupportedOperationException} at runtime, however inviting its signature looks.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ManualNavigationTest extends QuarkusBrowserlessTest {

    private Optional<Anchor> manualLink(Dialog dialog) {
        return find(Anchor.class, dialog).all().stream()
                .filter(a -> a.getId().filter(MissingDepartmentDialog.MANUAL_LINK_ID::equals).isPresent())
                .findFirst();
    }

    private static void assertOpensTheManualInANewTab(Anchor link) {
        assertEquals("/api/manual", link.getHref(), "the link points at the gated manual resource");
        assertTrue(link.isRouterIgnore(),
                "the Vaadin router would otherwise swallow /api/manual and show a page-not-found");
        assertEquals(Optional.of(AnchorTarget.BLANK.getValue()), link.getTarget(),
                "the manual opens in a new tab, so the notice is not navigated away from (UC-029 A7)");
        assertTrue(link.getText().contains(Translations.get("missingDepartmentDialog.manual")),
                "the label is translated, never a hard-coded literal (NFR-014)");
    }

    /** UC-029 A7: an administrator held by the notice is offered the manual beside the remedy. */
    @Test
    void administratorNoticeOffersTheManualWhenTheImageCarriesOne() {
        Dialog dialog = MissingDepartmentDialog.forAdministrator(true);

        Anchor link = manualLink(dialog).orElseThrow(
                () -> new AssertionError("the notice should offer the manual (UC-029 A7)"));
        assertOpensTheManualInANewTab(link);
        assertTrue(find(Button.class, dialog).all().stream()
                        .anyMatch(b -> b.getId().filter(MissingDepartmentDialog.ADD_BUTTON_ID::equals).isPresent()),
                "the manual is offered alongside the notice's own action, not instead of it");
    }

    /**
     * UC-029 A7 with BR-006: one manual serves both roles, so the reader who cannot create a
     * department -- the one most in need of the procedure -- is offered it too.
     */
    @Test
    void userNoticeOffersTheManualWhenTheImageCarriesOne() {
        Dialog dialog = MissingDepartmentDialog.forUser(true);

        Anchor link = manualLink(dialog).orElseThrow(
                () -> new AssertionError("the manual is offered to both console roles (UC-029 BR-006)"));
        assertOpensTheManualInANewTab(link);
    }

    /** UC-029 A1: a build that carries no manual leaves the entry out rather than linking to a 404. */
    @Test
    void noticeOmitsTheManualWhenTheImageCarriesNone() {
        assertTrue(manualLink(MissingDepartmentDialog.forAdministrator(false)).isEmpty(),
                "with no manual packaged the administrator's notice must not offer one (UC-029 A1)");
        assertTrue(manualLink(MissingDepartmentDialog.forUser(false)).isEmpty(),
                "with no manual packaged the user's notice must not offer one (UC-029 A1)");
    }

    /** UC-028 BR-110/BR-112 still hold: the manual link adds a way out to the document, not to the console. */
    @Test
    void theManualLinkDoesNotDisturbTheNoticesOwnActions() {
        Dialog dialog = MissingDepartmentDialog.forUser(true);

        assertFalse(dialog.isCloseOnEsc(), "the notice stays undismissable (UC-028 BR-110)");
        assertFalse(dialog.isCloseOnOutsideClick(), "the notice stays undismissable (UC-028 BR-110)");
        long actions = find(Button.class, dialog).all().stream()
                .map(Button::getId)
                .filter(id -> id.filter(MissingDepartmentDialog.LOGOUT_BUTTON_ID::equals).isPresent())
                .count();
        assertEquals(1, actions, "Logout is still the user's only action (UC-028 BR-112)");
    }
}
