package com.elicitsoftware.admin.flow;

import com.elicitsoftware.model.MessageTemplate;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@Route(value = "message-templates", layout = MainLayout.class)
@RolesAllowed("admin")
public class MessageTemplatesView extends VerticalLayout {

    private final Grid<MessageTemplate> grid = new Grid<>(MessageTemplate.class);

    public MessageTemplatesView() {
        setSizeFull();

        List<MessageTemplate> templates = MessageTemplate.listAll();

        grid.setItems(templates);
        grid.removeAllColumns();

        grid.addComponentColumn(template -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addClickListener(e ->
                    editTemplate(template)
            );
            return editBtn;
        }).setHeader("Edit").setAutoWidth(true);

        grid.addColumn(template -> template.id).setHeader("ID");
        grid.addColumn(template -> template.department.name).setHeader("Department").setSortable(true).setAutoWidth(true);
        grid.addColumn(template -> template.subject).setHeader("Subject").setSortable(true).setAutoWidth(true);
        grid.addColumn(template -> template.message).setHeader("Message").setSortable(true);
        grid.addColumn(template -> template.cronSchedule).setHeader("Cron Schedule").setSortable(true).setAutoWidth(true);
        grid.addColumn(template -> template.mimeType).setHeader("MIME Type").setSortable(true).setAutoWidth(true);

        add(grid);

        Button addBtn = new Button("New Message Template", e -> {
            editTemplate(new MessageTemplate());
        });
        add(addBtn);
    }

    private void editTemplate(MessageTemplate template) {
        String idParam = (template.id > 0) ? String.valueOf(template.id) : "0";
        getUI().ifPresent(ui ->
                ui.navigate("edit-message-template/" + idParam)
        );
    }
}
