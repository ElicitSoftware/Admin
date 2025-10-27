package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.model.MessageTemplate;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

/**
 * A Vaadin Flow view that displays a list of message templates in a grid format.
 * This view provides functionality to view, edit, and create new message templates
 * for administrative users.
 * 
 * <p>The view displays message templates in a sortable grid with the following columns:</p>
 * <ul>
 *   <li><strong>Edit:</strong> Button column with edit icons for each template</li>
 *   <li><strong>ID:</strong> Unique identifier of the message template</li>
 *   <li><strong>Department:</strong> The department associated with the template</li>
 *   <li><strong>Subject:</strong> The email/message subject line</li>
 *   <li><strong>MIME Type:</strong> Content type (HTML or plain text)</li>
 * </ul>
 * 
 * <p>The view is accessible at the "/message-templates" route and requires 
 * "elicit_admin" role for access. It includes functionality to create new templates
 * and edit existing ones through navigation to the edit view.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Full-screen layout optimization</li>
 *   <li>Sortable columns for department, subject, and MIME type</li>
 *   <li>Auto-width columns for optimal space utilization</li>
 *   <li>Direct navigation to template editing interface</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see MessageTemplate
 * @see EditMessageTemplatesView
 */
@Route(value = "message-templates", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class MessageTemplatesView extends VerticalLayout {

    /** Grid component for displaying message templates in a tabular format. */
    private final Grid<MessageTemplate> grid = new Grid<>(MessageTemplate.class);

    /**
     * Constructs a new MessageTemplatesView.
     * 
     * <p>Initializes the view with a full-screen layout and sets up a comprehensive
     * data grid for displaying message templates. The constructor performs the following
     * initialization steps:</p>
     * 
     * <ol>
     *   <li><strong>Layout Configuration:</strong> Sets the view to use full available space</li>
     *   <li><strong>Data Loading:</strong> Retrieves all message templates from the database</li>
     *   <li><strong>Grid Setup:</strong> Configures the grid with custom columns:
     *       <ul>
     *         <li>Edit button column with click handlers for template modification</li>
     *         <li>ID column displaying the template's unique identifier</li>
     *         <li>Department column showing associated department name (sortable)</li>
     *         <li>Subject column displaying email subject line (sortable)</li>
     *         <li>MIME Type column showing content type (sortable)</li>
     *       </ul>
     *   </li>
     *   <li><strong>Action Buttons:</strong> Adds a "New Message Template" button for creating new templates</li>
     * </ol>
     * 
     * <p>The grid is configured with auto-width columns and sorting capabilities to enhance
     * user experience. Edit buttons use Vaadin icons for intuitive visual design.</p>
     */
    public MessageTemplatesView() {
        setSizeFull();

        List<MessageTemplate> templates = MessageTemplate.listAll();

        grid.setItems(templates);
        grid.removeAllColumns();

        grid.addComponentColumn(template -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_ICON);
            editBtn.addClickListener(e ->
                    editTemplate(template)
            );
            return editBtn;
        }).setHeader("Edit").setAutoWidth(true);

        grid.addColumn(template -> template.id).setHeader("ID");
        grid.addColumn(template -> template.department.name).setHeader("Department").setSortable(true).setAutoWidth(true);
        grid.addColumn(template -> template.subject).setHeader("Subject").setSortable(true).setAutoWidth(true);
        grid.addColumn(template -> template.mimeType).setHeader("MIME Type").setSortable(true).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        add(grid);

        Button addBtn = new Button("New Message Template", e -> {
            editTemplate(new MessageTemplate());
        });
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(addBtn);
    }

    /**
     * Navigates to the message template editing view for the specified template.
     * 
     * <p>This method handles navigation to the {@link EditMessageTemplatesView} with
     * appropriate parameter passing to determine the editing mode:</p>
     * 
     * <ul>
     *   <li><strong>Edit Mode:</strong> If the template has a valid ID (> 0), navigates
     *       to edit the existing template</li>
     *   <li><strong>Create Mode:</strong> If the template has no ID or ID is 0, navigates
     *       to create a new template</li>
     * </ul>
     * 
     * <p>The method constructs the appropriate URL with the template ID parameter and
     * uses Vaadin's navigation system to route to the edit view. This ensures that
     * the edit view can properly determine whether to load existing data or initialize
     * a new template form.</p>
     * 
     * @param template the MessageTemplate to edit; if it's a new template (ID = 0),
     *                 the edit view will be opened in create mode
     * @see EditMessageTemplatesView
     */
    private void editTemplate(MessageTemplate template) {
        String idParam = (template.id > 0) ? String.valueOf(template.id) : "0";
        getUI().ifPresent(ui ->
                ui.navigate("edit-message-template/" + idParam)
        );
    }
}
