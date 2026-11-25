package com.elicitsoftware.admin.flow;

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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.ImportClient;
import com.elicitsoftware.security.ElicitRoles;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The list of import clients (UC-032): the machine clients of the registration API, each with
 * the principal name its tokens carry and the departments it may register into.
 *
 * <p>The page is the administrator's alone. It sits beside Users in the navigation because it
 * answers the same question for a different kind of caller: a person is entered there, a
 * machine client here, and a name is one or the other (BR-166).</p>
 *
 * @see EditImportClientView
 */
@Route(value = "import-clients", layout = MainLayout.class)
@RolesAllowed(ElicitRoles.ADMIN)
public class ImportClientsView extends VerticalLayout implements HasDynamicTitle {

    private final Grid<ImportClient> clientGrid = new Grid<>(ImportClient.class, false);

    /**
     * Builds the page: the explanatory paragraph, the grid and the Add button.
     */
    public ImportClientsView() {
        Paragraph info = new Paragraph(getTranslation("importClientsView.info"));
        info.addClassName("view-intro");
        configureGrid();
        add(info, clientGrid, createFooter());
        updateGrid();
    }

    private void configureGrid() {
        clientGrid.addComponentColumn(client -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_ICON);
            editBtn.setAriaLabel(getTranslation("common.edit"));
            editBtn.addClickListener(e -> edit(client.getId()));
            return editBtn;
        }).setHeader(getTranslation("common.edit")).setAutoWidth(true);
        clientGrid.addColumn(ImportClient::getName).setHeader(getTranslation("importClientsView.grid.name")).setAutoWidth(true).setSortable(true);
        clientGrid.addColumn(client -> client.getDescription() == null ? "" : client.getDescription())
                .setHeader(getTranslation("importClientsView.grid.description")).setAutoWidth(true).setSortable(true);
        clientGrid.addColumn(client -> client.isActive() ? getTranslation("common.yes") : getTranslation("common.no"))
                .setHeader(getTranslation("importClientsView.grid.active")).setAutoWidth(true).setSortable(true);
        clientGrid.addColumn(client -> client.getDepartments() == null ? ""
                        : client.getDepartments().stream().map(Department::getName).collect(Collectors.joining(", ")))
                .setHeader(getTranslation("importClientsView.grid.departments")).setAutoWidth(true).setSortable(true);

        clientGrid.setMultiSort(true);
        clientGrid.setWidthFull();
        clientGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
    }

    private void updateGrid() {
        // Eager load: a site has a handful of import clients.
        List<ImportClient> clients = ImportClient.listAll();
        clientGrid.setItems(clients);
    }

    private void edit(long id) {
        getUI().ifPresent(ui -> ui.navigate("edit-import-client/" + id));
    }

    private HorizontalLayout createFooter() {
        Button addBtn = new Button(getTranslation("importClientsView.add"), e -> edit(0));
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return new HorizontalLayout(addBtn);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("importClientsView.pageTitle");
    }
}
