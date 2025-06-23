package com.elicitsoftware.admin.flow;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/**
 * A Vaadin Flow view for editing department information.
 * This view provides functionality to create new departments or edit existing ones.
 * 
 * <p>The view is accessible at the "/edit-department" route with an optional ID parameter.
 * It requires "elicit_admin" role for access. The ID parameter determines whether this is
 * an edit operation (existing department) or a create operation (new department).</p>
 * 
 * <p>Route patterns:</p>
 * <ul>
 *   <li>/edit-department - Create a new department</li>
 *   <li>/edit-department/123 - Edit department with ID 123</li>
 * </ul>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@Route(value = "edit-department/:id?", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class EditDepartmentView extends VerticalLayout implements BeforeEnterObserver {

    /**
     * Called before the user enters this view.
     * This method is invoked by the Vaadin navigation lifecycle and is used to
     * process route parameters and initialize the view accordingly.
     * 
     * <p>The method should extract the department ID from the route parameters
     * (if present) and set up the view for either creating a new department
     * or editing an existing one based on the ID parameter.</p>
     * 
     * @param event the BeforeEnterEvent containing navigation information and route parameters
     * @see BeforeEnterObserver#beforeEnter(BeforeEnterEvent)
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {

    }
}
