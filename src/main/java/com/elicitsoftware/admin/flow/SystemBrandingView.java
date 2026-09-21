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

import com.elicitsoftware.diagnostics.BrandDiagnostics;
import com.elicitsoftware.security.ElicitRoles;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Branding diagnostics (UC-023): which brand directory resolved, the brand in use, and where
 * each asset came from, with an explicit reload (BR-092).
 */
@Route(value = "system/branding", layout = MainLayout.class)
@RolesAllowed(ElicitRoles.ADMIN)
public class SystemBrandingView extends VerticalLayout implements HasDynamicTitle {

    static final String SUMMARY_GRID_ID = "system-brand-summary";
    static final String ASSETS_GRID_ID = "system-brand-assets";
    static final String LOGO_ID = "system-brand-logo";
    static final String RELOAD_BUTTON_ID = "system-brand-reload";

    record Row(String label, String value) {
    }

    @Inject
    BrandDiagnostics brand;

    private final VerticalLayout body = new VerticalLayout();

    public SystemBrandingView() {
        // Size to the content, not the viewport, so the bottom padding follows the last grid.
        setWidthFull();
        setPadding(true);
        addClassName(LumoUtility.Padding.Bottom.XLARGE);
        body.setPadding(false);
    }

    @PostConstruct
    void init() {
        add(new H3("Branding"));
        add(new Paragraph("The brand resolves from the mounted directory first, then the local directory, "
                + "then the default packaged with the application. Each asset is listed on its own, because "
                + "a partial mount renders with the wrong fonts and no error."));
        Button reload = new Button("Reload brand", e -> {
            brand.reload();
            render();
            Notification.show("Brand cache discarded; the result below is freshly resolved.", 3000,
                    Notification.Position.MIDDLE);
        });
        reload.setId(RELOAD_BUTTON_ID);
        add(reload);
        add(body);
        render();
    }

    void render() {
        body.removeAll();
        BrandDiagnostics.BrandReport report = brand.report();

        body.add(new H4("Resolution"));
        Grid<Row> summary = new Grid<>();
        summary.setId(SUMMARY_GRID_ID);
        summary.setItems(List.of(
                new Row("Configured path (brand.file.system.path)", report.configuredPath()
                        + (report.externalExists() ? " (exists)" : " (not found)")),
                new Row("Local path (brand.local.path)", report.localPath()
                        + (report.localExists() ? " (exists)" : " (not found)")),
                new Row("Metadata file", report.metadataFile() != null ? report.metadataFile() : "none"),
                new Row("Brand name", valueOrUnknown(report.brandName())),
                new Row("Organization", valueOrUnknown(report.organization())),
                new Row("Brand version", valueOrUnknown(report.version())),
                new Row("In use (cached)", report.inUse().getDisplayName() + " [" + report.inUse().getBrandKey() + "]"),
                new Row("Summary", report.summary())));
        summary.addColumn(Row::label).setHeader("Item").setAutoWidth(true);
        summary.addColumn(Row::value).setHeader("Value").setFlexGrow(1);
        summary.setAllRowsVisible(true);
        summary.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        body.add(summary);

        body.add(new H4("Assets"));
        Grid<BrandDiagnostics.AssetReport> assets = new Grid<>();
        assets.setId(ASSETS_GRID_ID);
        assets.setItems(report.assets());
        assets.addColumn(BrandDiagnostics.AssetReport::role).setHeader("Asset").setAutoWidth(true);
        assets.addColumn(BrandDiagnostics.AssetReport::path).setHeader("Path").setAutoWidth(true);
        assets.addComponentColumn(a -> switch (a.source()) {
            case EXTERNAL -> SystemBadges.badge("Mounted", "badge success");
            case LOCAL -> SystemBadges.badge("Local", "badge success");
            case EMBEDDED -> SystemBadges.badge("Embedded default", "badge contrast");
            case ABSENT -> SystemBadges.badge("Absent", "badge error");
            case UNREADABLE -> SystemBadges.badge("Unreadable", "badge error");
        }).setHeader("Source").setAutoWidth(true);
        assets.addColumn(BrandDiagnostics.AssetReport::detail).setHeader("Location").setFlexGrow(1);
        assets.setAllRowsVisible(true);
        assets.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        body.add(assets);

        body.add(new H4("Logo preview"));
        Image logo = new Image("/api/brand/images/HorizontalLogo.png", "Brand logo");
        logo.setId(LOGO_ID);
        logo.setMaxHeight("80px");
        body.add(logo);
    }

    private static String valueOrUnknown(String value) {
        return value != null ? value : "unknown";
    }

    @Override
    public String getPageTitle() {
        return "System Branding";
    }
}
