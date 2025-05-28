package com.elicitsoftware.admin.flow;

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

public class PaginationControls extends HorizontalLayout {
    private final Span currentPageLabel = currentPageLabel();
    private int totalItemCount = 0;
    private int pageCount = 1;
    private int pageSize = 10;
    private int currentPage = 1;
    private Runnable pageChangedListener;
    private final Button firstPageButton = firstPageButton();

    public PaginationControls() {
        setDefaultVerticalComponentAlignment(Alignment.CENTER);
        setSpacing("0.3rem");
        setWidthFull();
        addToStart(createPageSizeField());
        addToEnd(firstPageButton, goToPreviousPageButton, currentPageLabel, goToNextPageButton, lastPageButton);
    }

    private final Button lastPageButton = lastPageButton();

    private Component createPageSizeField() {
        Select<Integer> select = new Select<>();
        select.addThemeVariants(SelectVariant.LUMO_SMALL);
        select.getStyle().set("--vaadin-input-field-value-font-size", "var(--lumo-font-size-s)");
        select.setWidth("4.8rem");
        select.setItems(1, 10, 15, 25, 50, 100);
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

    private final Button goToPreviousPageButton = goToPreviousPageButton();

    public void recalculatePageCount(int totalItemCount) {
        this.totalItemCount = totalItemCount;
        updatePageCount();
    }

    private final Button goToNextPageButton = goToNextPageButton();

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

    public int getPageSize() {
        return pageSize;
    }

    public int calculateOffset() {
        return (currentPage - 1) * pageSize;
    }

    private void updateControls() {
        currentPageLabel.setText(String.format("Page %d of %d", currentPage, pageCount));
        firstPageButton.setEnabled(currentPage > 1);
        lastPageButton.setEnabled(currentPage < pageCount);
        goToPreviousPageButton.setEnabled(currentPage > 1);
        goToNextPageButton.setEnabled(currentPage < pageCount);
    }

    private Button firstPageButton() {
        return createIconButton(VaadinIcon.ANGLE_DOUBLE_LEFT, "Go to first page", () -> currentPage = 1);
    }

    private Button lastPageButton() {
        return createIconButton(VaadinIcon.ANGLE_DOUBLE_RIGHT, "Go to last page", () -> currentPage = pageCount);
    }

    private Button goToNextPageButton() {
        return createIconButton(VaadinIcon.ANGLE_RIGHT, "Go to next page", () -> currentPage++);
    }

    private Button goToPreviousPageButton() {
        return createIconButton(VaadinIcon.ANGLE_LEFT, "Go to previous page", () -> currentPage--);
    }

    private Span currentPageLabel() {
        var label = new Span();
        label.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.Padding.Horizontal.SMALL);
        return label;
    }

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

    private void firePageChangedEvent() {
        if (pageChangedListener != null) {
            pageChangedListener.run();
        }
    }

    public void onPageChanged(Runnable pageChangedListener) {
        this.pageChangedListener = pageChangedListener;
    }

    public void resetToFirstPage() {
        this.currentPage = 1;
    }


}