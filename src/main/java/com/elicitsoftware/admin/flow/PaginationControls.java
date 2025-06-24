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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * A reusable pagination control component for navigating through large datasets.
 * This component provides a complete pagination interface with page navigation buttons,
 * page size selection, and current page information display.
 * 
 * <p>The component features:</p>
 * <ul>
 *   <li><strong>Page Size Selection:</strong> Dropdown to choose items per page (5, 10, 15, 25, 50, 100)</li>
 *   <li><strong>Navigation Buttons:</strong> First page, previous page, next page, and last page controls</li>
 *   <li><strong>Page Information:</strong> Current page and total page count display</li>
 *   <li><strong>Accessibility:</strong> Proper ARIA labels and semantic structure</li>
 *   <li><strong>Responsive Design:</strong> Adapts to different screen sizes</li>
 * </ul>
 * 
 * <p>The component automatically calculates pagination parameters and provides methods
 * to integrate with data loading operations. It uses a callback mechanism to notify
 * parent components when pagination state changes.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * PaginationControls pagination = new PaginationControls();
 * pagination.recalculatePageCount(totalItems);
 * pagination.onPageChanged(() -> loadData(pagination.calculateOffset(), pagination.getPageSize()));
 * }</pre>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see HorizontalLayout
 */
public class PaginationControls extends HorizontalLayout {
    /** Label displaying current page information (e.g., "Page 2 of 5"). */
    private final Span currentPageLabel = currentPageLabel();
    
    /** Total number of items across all pages. */
    private int totalItemCount = 0;
    
    /** Total number of pages based on item count and page size. */
    private int pageCount = 1;
    
    /** Number of items to display per page (default: 10). */
    private int pageSize = 10;
    
    /** Currently active page number (1-based). */
    private int currentPage = 1;
    
    /** Callback function executed when pagination state changes. */
    private Runnable pageChangedListener;
    
    /** Button for navigating to the first page. */
    private final Button firstPageButton = firstPageButton();

    /**
     * Constructs a new PaginationControls component.
     * 
     * <p>Initializes the pagination interface with the following layout:</p>
     * <ul>
     *   <li><strong>Left side:</strong> Page size selection dropdown</li>
     *   <li><strong>Right side:</strong> Navigation controls (first, previous, current page, next, last)</li>
     * </ul>
     * 
     * <p>The component is configured with:</p>
     * <ul>
     *   <li>Center-aligned vertical alignment for consistent visual appearance</li>
     *   <li>Full width layout with appropriate spacing</li>
     *   <li>Default page size of 10 items</li>
     *   <li>Starting on page 1</li>
     * </ul>
     */
    public PaginationControls() {
        setDefaultVerticalComponentAlignment(Alignment.CENTER);
        setSpacing("0.3rem");
        setWidthFull();
        addToStart(createPageSizeField());
        addToEnd(firstPageButton, goToPreviousPageButton, currentPageLabel, goToNextPageButton, lastPageButton);
    }

    /** Button for navigating to the last page. */
    private final Button lastPageButton = lastPageButton();

    /**
     * Creates and configures the page size selection component.
     * 
     * <p>This method builds a dropdown selector that allows users to choose how many
     * items to display per page. The component includes:</p>
     * <ul>
     *   <li><strong>Dropdown options:</strong> 5, 10, 15, 25, 50, 100 items per page</li>
     *   <li><strong>Default selection:</strong> Current page size value</li>
     *   <li><strong>Accessibility:</strong> Proper labeling and ARIA attributes</li>
     *   <li><strong>Styling:</strong> Small theme variant with custom width and font size</li>
     * </ul>
     * 
     * <p>When the user changes the page size, the component automatically recalculates
     * the total page count and triggers the page changed event.</p>
     * 
     * @return a HorizontalLayout containing the page size label and selection dropdown
     */
    private Component createPageSizeField() {
        Select<Integer> select = new Select<>();
        select.addThemeVariants(SelectVariant.LUMO_SMALL);
        select.getStyle().set("--vaadin-input-field-value-font-size", "var(--lumo-font-size-s)");
        select.setWidth("4.8rem");
        select.setItems(5, 10, 15, 25, 50, 100);
        select.setValue(pageSize);
        select.addValueChangeListener(e -> {
            pageSize = e.getValue();
            updatePageCount();
        });
        var label = new Span("Page size");
        label.setId("page-size-label");
        label.addClassName(LumoUtility.FontSize.SMALL);
        select.setAriaLabelledBy("page-size-label");
        final HorizontalLayout layout = new HorizontalLayout(Alignment.CENTER, label, select);
        layout.setSpacing(false);
        layout.getThemeList().add("spacing-s");
        return layout;
    }

    /** Button for navigating to the previous page. */
    private final Button goToPreviousPageButton = goToPreviousPageButton();

    /**
     * Recalculates pagination parameters based on the total number of items.
     * 
     * <p>This method should be called whenever the underlying dataset changes.
     * It updates the total item count and triggers a recalculation of:</p>
     * <ul>
     *   <li>Total page count based on current page size</li>
     *   <li>Current page validity (adjusts if current page exceeds new page count)</li>
     *   <li>Control states (enabled/disabled state of navigation buttons)</li>
     * </ul>
     * 
     * <p>After recalculation, the page changed event is fired to notify listeners
     * that they may need to reload data for the current pagination state.</p>
     * 
     * @param totalItemCount the total number of items in the dataset
     */
    public void recalculatePageCount(int totalItemCount) {
        this.totalItemCount = totalItemCount;
        updatePageCount();
    }

    /** Button for navigating to the next page. */
    private final Button goToNextPageButton = goToNextPageButton();

    /**
     * Updates the total page count and adjusts current page if necessary.
     * 
     * <p>This internal method recalculates the page count using the current
     * total item count and page size. The calculation handles edge cases:</p>
     * <ul>
     *   <li><strong>Empty dataset:</strong> Shows 1 page even with 0 items</li>
     *   <li><strong>Page overflow:</strong> Adjusts current page if it exceeds the new page count</li>
     * </ul>
     * 
     * <p>After updating the page count, this method refreshes the UI controls
     * and notifies listeners of the pagination state change.</p>
     */
    private void updatePageCount() {
        if (totalItemCount == 0) {
            this.pageCount = 1; // we still want to display one page even though there are no items
        } else {
            this.pageCount = (int) Math.ceil((double) totalItemCount / pageSize);
        }
        if (currentPage > pageCount) {
            currentPage = pageCount;
        }
        updateControls();
        firePageChangedEvent();
    }

    /**
     * Gets the current page size setting.
     * 
     * @return the number of items displayed per page
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Calculates the offset for database queries based on current pagination state.
     * 
     * <p>This method computes the number of items to skip when performing paginated
     * database queries. The offset is calculated as: (currentPage - 1) × pageSize</p>
     * 
     * <p><strong>Example:</strong> For page 3 with page size 10, the offset would be 20,
     * meaning the query should skip the first 20 items and return the next 10.</p>
     * 
     * @return the number of items to skip in a paginated query
     */
    public int calculateOffset() {
        return (currentPage - 1) * pageSize;
    }

    /**
     * Updates the state and appearance of all pagination controls.
     * 
     * <p>This method refreshes the user interface to reflect the current pagination state:</p>
     * <ul>
     *   <li><strong>Page label:</strong> Updates the "Page X of Y" display</li>
     *   <li><strong>Button states:</strong> Enables/disables navigation buttons based on current position:
     *       <ul>
     *         <li>First page and previous page buttons are disabled on page 1</li>
     *         <li>Last page and next page buttons are disabled on the final page</li>
     *       </ul>
     *   </li>
     * </ul>
     * 
     * <p>This method is called automatically whenever pagination state changes.</p>
     */
    private void updateControls() {
        currentPageLabel.setText(String.format("Page %d of %d", currentPage, pageCount));
        firstPageButton.setEnabled(currentPage > 1);
        lastPageButton.setEnabled(currentPage < pageCount);
        goToPreviousPageButton.setEnabled(currentPage > 1);
        goToNextPageButton.setEnabled(currentPage < pageCount);
    }

    /**
     * Creates the first page navigation button.
     * 
     * @return a button that navigates to the first page when clicked
     */
    private Button firstPageButton() {
        return createIconButton(VaadinIcon.ANGLE_DOUBLE_LEFT, "Go to first page", () -> currentPage = 1);
    }

    /**
     * Creates the last page navigation button.
     * 
     * @return a button that navigates to the last page when clicked
     */
    private Button lastPageButton() {
        return createIconButton(VaadinIcon.ANGLE_DOUBLE_RIGHT, "Go to last page", () -> currentPage = pageCount);
    }

    /**
     * Creates the next page navigation button.
     * 
     * @return a button that navigates to the next page when clicked
     */
    private Button goToNextPageButton() {
        return createIconButton(VaadinIcon.ANGLE_RIGHT, "Go to next page", () -> currentPage++);
    }

    /**
     * Creates the previous page navigation button.
     * 
     * @return a button that navigates to the previous page when clicked
     */
    private Button goToPreviousPageButton() {
        return createIconButton(VaadinIcon.ANGLE_LEFT, "Go to previous page", () -> currentPage--);
    }

    /**
     * Creates and configures the current page information label.
     * 
     * <p>This method creates a span element that displays the current page information
     * in the format "Page X of Y". The label is styled with:</p>
     * <ul>
     *   <li>Small font size for compact display</li>
     *   <li>Horizontal padding for proper spacing</li>
     * </ul>
     * 
     * @return a Span component for displaying current page information
     */
    private Span currentPageLabel() {
        var label = new Span();
        label.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Padding.Horizontal.SMALL);
        return label;
    }

    /**
     * Creates a standardized icon button for pagination navigation.
     * 
     * <p>This helper method creates navigation buttons with consistent styling and behavior:</p>
     * <ul>
     *   <li><strong>Icon:</strong> Uses the specified Vaadin icon</li>
     *   <li><strong>Styling:</strong> Applies icon and small theme variants</li>
     *   <li><strong>Accessibility:</strong> Sets appropriate ARIA label for screen readers</li>
     *   <li><strong>Behavior:</strong> Executes the provided action and updates UI state</li>
     * </ul>
     * 
     * <p>When clicked, the button executes the navigation action, updates all controls
     * to reflect the new state, and fires the page changed event.</p>
     * 
     * @param icon the Vaadin icon to display on the button
     * @param ariaLabel the accessibility label for screen readers
     * @param onClickListener the action to execute when the button is clicked
     * @return a configured Button component for pagination navigation
     */
    private Button createIconButton(VaadinIcon icon, String ariaLabel, Runnable onClickListener) {
        Button button = new Button(new Icon(icon));
        button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_SMALL);
        button.addClickListener(e -> {
            onClickListener.run();
            updateControls();
            firePageChangedEvent();
        });
        button.setAriaLabel(ariaLabel);
        return button;
    }

    /**
     * Notifies registered listeners that the pagination state has changed.
     * 
     * <p>This method is called automatically whenever pagination parameters change,
     * such as when the user navigates to a different page or changes the page size.
     * It allows parent components to respond to pagination changes by reloading
     * data or updating their display.</p>
     */
    private void firePageChangedEvent() {
        if (pageChangedListener != null) {
            pageChangedListener.run();
        }
    }

    /**
     * Registers a callback to be executed when pagination state changes.
     * 
     * <p>This method allows parent components to register a listener that will be
     * called whenever the pagination state changes. The listener is typically used
     * to reload data based on the new pagination parameters.</p>
     * 
     * <p><strong>Example usage:</strong></p>
     * <pre>{@code
     * pagination.onPageChanged(() -> {
     *     loadData(pagination.calculateOffset(), pagination.getPageSize());
     * });
     * }</pre>
     * 
     * @param pageChangedListener the callback to execute when pagination changes
     */
    public void onPageChanged(Runnable pageChangedListener) {
        this.pageChangedListener = pageChangedListener;
    }

    /**
     * Resets the pagination to the first page.
     * 
     * <p>This method sets the current page back to 1 without triggering
     * a page changed event. It's useful when resetting pagination state
     * after applying new filters or search criteria.</p>
     * 
     * <p><strong>Note:</strong> This method only changes the current page number.
     * To update the UI and notify listeners, call {@link #recalculatePageCount(int)}
     * or manually trigger {@link #updateControls()} and {@link #firePageChangedEvent()}.</p>
     */
    public void resetToFirstPage() {
        this.currentPage = 1;
    }


}
