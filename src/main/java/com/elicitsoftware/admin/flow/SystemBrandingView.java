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
        add(new H3(getTranslation("systemBrandingView.title")));
        add(new Paragraph(getTranslation("systemBrandingView.intro")));
        Button reload = new Button(getTranslation("systemBrandingView.reload"), e -> {
            brand.reload();
            render();
            Notification.show(getTranslation("systemBrandingView.reloaded"), 3000, Notification.Position.MIDDLE);
        });
        reload.setId(RELOAD_BUTTON_ID);
        add(reload);
        add(body);
        render();
    }

    void render() {
        body.removeAll();
        BrandDiagnostics.BrandReport report = brand.report();

        body.add(new H4(getTranslation("systemBrandingView.resolution")));
        Grid<Row> summary = new Grid<>();
        summary.setId(SUMMARY_GRID_ID);
        summary.setItems(List.of(
                new Row(getTranslation("systemBrandingView.row.configuredPath"),
                        pathState(report.configuredPath(), report.externalExists())),
                new Row(getTranslation("systemBrandingView.row.localPath"),
                        pathState(report.localPath(), report.localExists())),
                new Row(getTranslation("systemBrandingView.row.metadataFile"),
                        report.metadataFile() != null ? report.metadataFile() : getTranslation("common.none")),
                new Row(getTranslation("systemBrandingView.row.brandName"), valueOrUnknown(report.brandName())),
                new Row(getTranslation("systemBrandingView.row.organization"), valueOrUnknown(report.organization())),
                new Row(getTranslation("systemBrandingView.row.brandVersion"), valueOrUnknown(report.version())),
                new Row(getTranslation("systemBrandingView.row.inUse"), getTranslation("systemBrandingView.inUse",
                        report.inUse().getDisplayName(getLocale()), report.inUse().getBrandKey())),
                new Row(getTranslation("systemBrandingView.row.summary"), report.summary())));
        summary.addColumn(Row::label).setHeader(getTranslation("system.grid.item")).setAutoWidth(true);
        summary.addColumn(Row::value).setHeader(getTranslation("system.grid.value")).setFlexGrow(1);
        summary.setAllRowsVisible(true);
        summary.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        body.add(summary);

        body.add(new H4(getTranslation("systemBrandingView.assets")));
        Grid<BrandDiagnostics.AssetReport> assets = new Grid<>();
        assets.setId(ASSETS_GRID_ID);
        assets.setItems(report.assets());
        assets.addColumn(BrandDiagnostics.AssetReport::role)
                .setHeader(getTranslation("systemBrandingView.grid.asset")).setAutoWidth(true);
        assets.addColumn(BrandDiagnostics.AssetReport::path)
                .setHeader(getTranslation("systemBrandingView.grid.path")).setAutoWidth(true);
        assets.addComponentColumn(a -> switch (a.source()) {
            case EXTERNAL -> SystemBadges.badge(getTranslation("systemBrandingView.source.mounted"), "success");
            case LOCAL -> SystemBadges.badge(getTranslation("systemBrandingView.source.local"), "success");
            case EMBEDDED -> SystemBadges.badge(getTranslation("systemBrandingView.source.embedded"), "contrast");
            case ABSENT -> SystemBadges.badge(getTranslation("systemBrandingView.source.absent"), "error");
            case UNREADABLE -> SystemBadges.badge(getTranslation("systemBrandingView.source.unreadable"), "error");
        }).setHeader(getTranslation("systemBrandingView.grid.source")).setAutoWidth(true);
        assets.addColumn(BrandDiagnostics.AssetReport::detail)
                .setHeader(getTranslation("systemBrandingView.grid.location")).setFlexGrow(1);
        assets.setAllRowsVisible(true);
        assets.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        body.add(assets);

        body.add(new H4(getTranslation("systemBrandingView.logoPreview")));
        Image logo = new Image("/api/brand/images/HorizontalLogo.png", getTranslation("systemBrandingView.logoAlt"));
        logo.setId(LOGO_ID);
        logo.setMaxHeight("80px");
        body.add(logo);
    }

    private String pathState(String path, boolean exists) {
        return getTranslation(exists ? "systemBrandingView.path.exists" : "systemBrandingView.path.notFound", path);
    }

    private String valueOrUnknown(String value) {
        return value != null ? value : getTranslation("common.unknown");
    }

    @Override
    public String getPageTitle() {
        return getTranslation("systemBrandingView.pageTitle");
    }
}
