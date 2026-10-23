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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextAreaVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-007: Manage Message Templates — {@link EditMessageTemplatesView} in a plain browserless UI
 * with a stand-in session login. Covers the form, the content types
 * on offer (BR-027), the departments limited to the editor's own (BR-028) and the live preview
 * that renders HTML as markup and plain text as text.
 *
 * <p>Not reached here: the binder and everything after it in {@code init} (validation, the
 * button wiring), because binding by property name needs the accessors Panache's enhancement
 * adds; {@code beforeEnter}, which starts with {@code MessageTemplate.findById} or
 * {@code MessageType.findById}; and saving, which ends in {@code persistAndFlush} or the entity
 * manager.</p>
 */
class EditMessageTemplatesViewUnitTest extends PlainSmallViewTestSupport {

    private Department cardiology;
    private Department oncology;
    private EditMessageTemplatesView view;

    @BeforeEach
    void build() {
        cardiology = department(1, "Cardiology");
        oncology = department(2, "Oncology");
        User user = new User();
        user.setUsername("ada");
        Set<Department> departments = new LinkedHashSet<>(List.of(cardiology, oncology));
        user.setDepartments(departments);
        view = new EditMessageTemplatesView();
        view.uiSessionLogin = new UiSessionLogin() {
            @Override
            public User getUser() {
                return user;
            }
        };
        // The binder binds by bean property name ("subject", "message", ...). MessageTemplate
        // declares public fields only; the getters and setters a property set needs are added by
        // Panache's build-time enhancement, which a plain JVM does not run. init() therefore stops
        // at the first bind, after the layout and the preview listeners are in place.
        IllegalArgumentException unenhanced = assertThrows(IllegalArgumentException.class, view::init);
        assertTrue(unenhanced.getMessage().contains("subject"), unenhanced.getMessage());
        UI.getCurrent().add(view);
    }

    private static Department department(long id, String name) {
        Department d = new Department();
        d.id = id;
        d.name = name;
        return d;
    }

    private FormLayout form() {
        HorizontalLayout main = (HorizontalLayout) view.getComponentAt(0);
        return (FormLayout) main.getComponentAt(0);
    }

    private VerticalLayout previewColumn() {
        HorizontalLayout main = (HorizontalLayout) view.getComponentAt(0);
        return (VerticalLayout) main.getComponentAt(1);
    }

    private List<Component> formParts() {
        return form().getChildren().toList();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> mimeType() {
        return (ComboBox<String>) formParts().get(0);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<Department> departmentBox() {
        return (ComboBox<Department>) formParts().get(1);
    }

    private TextField subject() {
        return (TextField) formParts().get(2);
    }

    private TextArea body() {
        return (TextArea) formParts().get(3);
    }

    private Button save() {
        return (Button) formParts().get(4);
    }

    private Button update() {
        return (Button) formParts().get(5);
    }

    private Div preview() {
        return (Div) previewColumn().getComponentAt(1);
    }

    @Test
    void given_editor_when_opened_then_formIsLabelledAndLaidOutInTwoColumns() {
        assertEquals(t("editMessageTemplatesView.mimeType"), mimeType().getLabel());
        assertEquals(t("editMessageTemplatesView.department"), departmentBox().getLabel());
        assertEquals(t("editMessageTemplatesView.subject"), subject().getLabel());
        assertEquals(t("editMessageTemplatesView.body"), body().getLabel());
        assertEquals(t("common.save"), save().getText());
        assertEquals(t("editMessageTemplatesView.btnUpdate"), update().getText());
        assertTrue(subject().getThemeNames().contains(TextFieldVariant.LUMO_SMALL.getVariantName()));
        assertTrue(body().getThemeNames().contains(TextAreaVariant.LUMO_SMALL.getVariantName()));
        assertTrue(mimeType().getThemeNames().contains(ComboBoxVariant.LUMO_SMALL.getVariantName()));
        assertTrue(departmentBox().getThemeNames().contains(ComboBoxVariant.LUMO_SMALL.getVariantName()));
        assertFalse(mimeType().isAllowCustomValue());
        assertFalse(departmentBox().isAllowCustomValue());
        assertTrue(departmentBox().isRequiredIndicatorVisible());
        assertEquals(ValueChangeMode.TIMEOUT, body().getValueChangeMode());
        assertEquals(300, body().getValueChangeTimeout());

        assertTrue(form().hasClassName("template-column"));
        assertEquals("100%", form().getWidth());
        assertEquals(1, form().getResponsiveSteps().size());
        HorizontalLayout main = (HorizontalLayout) view.getComponentAt(0);
        assertEquals("100%", main.getWidth());
        assertEquals(1.0, main.getFlexGrow(form()));
        assertEquals(1.0, main.getFlexGrow(previewColumn()));
    }

    @Test
    void given_editor_when_opened_then_onlyTheTwoContentTypesAndTheEditorsDepartmentsAreOffered() {
        assertEquals(List.of("text/html", "text/plain"), mimeType().getListDataView().getItems().toList());
        assertEquals(List.of(cardiology, oncology), departmentBox().getListDataView().getItems().toList());
        assertEquals("Oncology", departmentBox().getItemLabelGenerator().apply(oncology));
    }

    @Test
    void given_editor_when_opened_then_previewColumnIsEmptyAndMarkedAsContent() {
        VerticalLayout column = previewColumn();
        assertTrue(column.hasClassName("template-column"));
        assertEquals("100%", column.getWidth());
        assertEquals(t("editMessageTemplatesView.preview"), ((H3) column.getComponentAt(0)).getText());
        Div preview = preview();
        assertTrue(preview.getElement().hasAttribute("data-i18n-content"));
        assertEquals("100%", preview.getWidth());
        assertEquals("100%", preview.getHeight());
        assertEquals("", preview.getElement().getProperty("innerHTML"));
    }

    @Test
    void given_htmlType_when_bodyTyped_then_previewRendersItAsMarkup() {
        mimeType().setValue("text/html");

        body().setValue("<b>Hello</b>");

        assertEquals("<b>Hello</b>", preview().getElement().getProperty("innerHTML"));
        assertEquals("", preview().getElement().getText());
    }

    @Test
    void given_noType_when_bodyTyped_then_previewRendersItAsMarkup() {
        body().setValue("<i>x</i>");

        assertEquals("<i>x</i>", preview().getElement().getProperty("innerHTML"));
    }

    @Test
    void given_markupBody_when_typeSwitchedToPlain_then_previewShowsTheTextLiterally() {
        body().setValue("<b>Hello</b>");

        mimeType().setValue("text/plain");

        assertEquals("<b>Hello</b>", preview().getElement().getText());
        assertEquals("", preview().getElement().getProperty("innerHTML"));

        mimeType().setValue("text/html");

        assertEquals("<b>Hello</b>", preview().getElement().getProperty("innerHTML"));
        assertEquals("", preview().getElement().getText());
    }

}
