package com.elicitsoftware.admin.i18n;

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

import com.elicitsoftware.admin.flow.DepartmentsView;
import com.elicitsoftware.admin.flow.EditDepartmentView;
import com.elicitsoftware.admin.flow.EditMessageTemplatesView;
import com.elicitsoftware.admin.flow.EditUserView;
import com.elicitsoftware.admin.flow.MessageTemplatesView;
import com.elicitsoftware.admin.flow.RegisterView;
import com.elicitsoftware.admin.flow.RespondentImportView;
import com.elicitsoftware.admin.flow.SearchView;
import com.elicitsoftware.admin.flow.SurveyDefinitionApplyView;
import com.elicitsoftware.admin.flow.SurveyDefinitionExportView;
import com.elicitsoftware.admin.flow.UnauthorizedView;
import com.elicitsoftware.admin.flow.UsersView;
import com.elicitsoftware.model.User;
import com.elicitsoftware.test.PostgresTestResource;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.VaadinSession;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-020 / NFR-011 backstop: with the pseudo-locale every translated text renders as
 * {@code ⟦key⟧}, so any prose-like text on a rendered route that lacks the marker bypassed the
 * provider. Subtrees flagged {@code data-i18n-content} carry stored data and are skipped.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DisplayedStringsSweepTest extends QuarkusBrowserlessTest {

    private static final Pattern PROSE_LIKE = Pattern.compile("^(?=.*\\p{L})(.*\\s.*|\\p{Lu}.*|.*[.:!?])$", Pattern.DOTALL);
    private static final List<String> TEXT_ATTRIBUTES = List.of(
            "label", "placeholder", "helper-text", "aria-label", "title", "alt", "error-message");
    private static final List<String> TEXT_PROPERTIES = List.of(
            "label", "placeholder", "helperText", "errorMessage", "innerHTML", "textContent");

    private static void seedUser(String name) {
        User user = new User();
        user.setId(1);
        user.setUsername(name);
        user.setActive(true);
        VaadinSession.getCurrent().setAttribute("user", user);
    }

    @Test
    @TestSecurity(user = "sweep.admin", roles = {"elicit_admin", "elicit_user"})
    void adminRoutes_haveNoUntranslatedText() {
        seedUser("sweep.admin");
        UI.getCurrent().setLocale(ElicitI18NProvider.PSEUDO_LOCALE);
        List<String> problems = new ArrayList<>();
        List<Class<? extends Component>> routes = List.of(
                SearchView.class, RegisterView.class, DepartmentsView.class, EditDepartmentView.class,
                MessageTemplatesView.class, EditMessageTemplatesView.class, UsersView.class, EditUserView.class,
                RespondentImportView.class, SurveyDefinitionApplyView.class, SurveyDefinitionExportView.class,
                UnauthorizedView.class);
        for (Class<? extends Component> route : routes) {
            navigate(route);
            sweep(route.getSimpleName(), problems);
        }
        assertTrue(problems.isEmpty(), report(problems));
    }

    private void sweep(String route, List<String> problems) {
        UI ui = UI.getCurrent();
        check(route, "<page title>", ui.getInternals().getTitle(), problems);
        walk(route, ui.getElement(), problems);
        for (Grid<?> grid : find(Grid.class).all().stream().map(g -> (Grid<?>) g).toList()) {
            grid.getColumns().forEach(c -> check(route, "grid header", c.getHeaderText(), problems));
        }
    }

    private void walk(String route, Element element, List<String> problems) {
        if (element.isTextNode()) {
            check(route, "text", element.getText(), problems);
            return;
        }
        if (element.hasAttribute("data-i18n-content")) {
            return;
        }
        String where = element.getTag() + (element.getAttribute("id") != null ? "#" + element.getAttribute("id") : "");
        for (String attr : TEXT_ATTRIBUTES) {
            if (element.hasAttribute(attr)) {
                check(route, where + "[" + attr + "]", element.getAttribute(attr), problems);
            }
        }
        for (String prop : TEXT_PROPERTIES) {
            if (element.hasProperty(prop)) {
                check(route, where + "." + prop, element.getProperty(prop), problems);
            }
        }
        for (int i = 0; i < element.getChildCount(); i++) {
            walk(route, element.getChild(i), problems);
        }
    }

    private static void check(String route, String where, String value, List<String> problems) {
        if (value == null || value.isBlank()) {
            return;
        }
        String stripped = value.replaceAll("<[^>]+>", " ").strip();
        if (stripped.contains(ElicitI18NProvider.PSEUDO_OPEN) || !PROSE_LIKE.matcher(stripped).matches()) {
            return;
        }
        problems.add(route + " " + where + ": \"" + stripped + "\"");
    }

    private static String report(List<String> problems) {
        return problems.size() + " untranslated text(s) rendered (UC-020 / NFR-011):\n  " + String.join("\n  ", problems);
    }
}
