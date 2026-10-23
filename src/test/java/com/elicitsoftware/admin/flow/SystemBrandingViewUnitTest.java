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

import com.elicitsoftware.admin.util.BrandUtil;
import com.elicitsoftware.diagnostics.BrandDiagnostics;
import com.elicitsoftware.diagnostics.BrandDiagnostics.AssetReport;
import com.elicitsoftware.diagnostics.BrandDiagnostics.BrandReport;
import com.elicitsoftware.diagnostics.BrandDiagnostics.Source;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-023: Diagnose Branding — {@link SystemBrandingView} in a plain browserless UI with a stand-in
 * for the brand diagnostics. Covers the resolution summary, every asset reporting its source
 * (BR-091), the logo preview and the explicit reload that discards the cache and re-renders
 * (BR-092).
 */
class SystemBrandingViewUnitTest extends PlainSmallViewTestSupport {

    private static final BrandUtil.BrandInfo IN_USE = new BrandUtil.BrandInfo("acme", "Acme Health", "/l.png", "acme");

    private BrandReport report = new BrandReport("/brand", "brand", true, false, "brand-info.json", "Acme",
            "Acme Org", "2.0", IN_USE, List.of(
            new AssetReport("theme.css", "Theme", Source.EXTERNAL, "/brand/theme.css"),
            new AssetReport("colors.css", "Colors", Source.LOCAL, "brand/colors.css"),
            new AssetReport("favicon.ico", "Favicon", Source.EMBEDDED, "classpath"),
            new AssetReport("logo.png", "Logo", Source.ABSENT, "-"),
            new AssetReport("icon.png", "Icon", Source.UNREADABLE, "permission denied")));
    private int reloads;
    private SystemBrandingView view;

    private SystemBrandingView init() {
        view = new SystemBrandingView();
        view.brand = new BrandDiagnostics() {
            @Override
            public BrandReport report() {
                return report;
            }

            @Override
            public void reload() {
                reloads++;
            }
        };
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    @SuppressWarnings("unchecked")
    private <T> Grid<T> grid(String id) {
        return (Grid<T>) byId(view, id, Grid.class);
    }

    @Test
    void given_mountedBrand_when_opened_then_resolutionFactsAreShown() {
        init();

        List<Component> parts = view.getChildren().toList();
        assertEquals(t("systemBrandingView.title"), ((H3) parts.get(0)).getText());
        assertEquals(t("systemBrandingView.intro"), ((Paragraph) parts.get(1)).getText());
        Button reload = (Button) parts.get(2);
        assertEquals(SystemBrandingView.RELOAD_BUTTON_ID, reload.getId().orElseThrow());
        assertEquals(t("systemBrandingView.reload"), reload.getText());
        VerticalLayout body = (VerticalLayout) parts.get(3);
        assertFalse(body.isPadding());
        assertTrue(view.hasClassName("system-view"));
        assertEquals(t("systemBrandingView.pageTitle"), view.getPageTitle());

        List<Component> bodyParts = body.getChildren().toList();
        assertEquals(t("systemBrandingView.resolution"), ((H4) bodyParts.get(0)).getText());
        assertEquals(t("systemBrandingView.assets"), ((H4) bodyParts.get(2)).getText());
        assertEquals(t("systemBrandingView.logoPreview"), ((H4) bodyParts.get(4)).getText());

        Grid<SystemBrandingView.Row> summary = grid(SystemBrandingView.SUMMARY_GRID_ID);
        assertSummaryGrid(summary);
        assertSummaryGrid(grid(SystemBrandingView.ASSETS_GRID_ID));
        assertEquals(List.of(t("system.grid.item"), t("system.grid.value")), headers(summary));
        assertEquals(List.of(t("systemBrandingView.row.configuredPath"), t("systemBrandingView.row.localPath"),
                t("systemBrandingView.row.metadataFile"), t("systemBrandingView.row.brandName"),
                t("systemBrandingView.row.organization"), t("systemBrandingView.row.brandVersion"),
                t("systemBrandingView.row.inUse"), t("systemBrandingView.row.summary")), column(summary, 0));
        assertEquals(List.of(t("systemBrandingView.path.exists", "/brand"), t("systemBrandingView.path.notFound", "brand"),
                "brand-info.json", "Acme", "Acme Org", "2.0", t("systemBrandingView.inUse", "Acme Health", "acme"),
                t("systemBrandingView.summary.mountedNamed", "Acme", "/brand")), column(summary, 1));

        Image logo = byId(view, SystemBrandingView.LOGO_ID, Image.class);
        assertEquals("/api/brand/images/HorizontalLogo.png", logo.getSrc());
        assertEquals(t("systemBrandingView.logoAlt"), logo.getAlt().orElseThrow());
        assertEquals("80px", logo.getMaxHeight());
    }

    @Test
    void given_noMetadata_when_opened_then_missingValuesAreWordedAsNoneOrUnknown() {
        report = new BrandReport("/brand", "brand", false, true, null, null, null, null, IN_USE, List.of());
        init();

        assertEquals(List.of(t("systemBrandingView.path.notFound", "/brand"), t("systemBrandingView.path.exists", "brand"),
                        t("common.none"), t("common.unknown"), t("common.unknown"), t("common.unknown")),
                column(grid(SystemBrandingView.SUMMARY_GRID_ID), 1).subList(0, 6));
    }

    @Test
    void given_assetsFromEverySource_when_opened_then_eachShowsItsSourceBadge() {
        init();

        Grid<AssetReport> assets = grid(SystemBrandingView.ASSETS_GRID_ID);
        assertEquals(List.of(t("systemBrandingView.grid.asset"), t("systemBrandingView.grid.path"),
                t("systemBrandingView.grid.source"), t("systemBrandingView.grid.location")), headers(assets));
        assertEquals(List.of("Theme", "Colors", "Favicon", "Logo", "Icon"), column(assets, 0));
        assertEquals(List.of("theme.css", "colors.css", "favicon.ico", "logo.png", "icon.png"), column(assets, 1));
        assertEquals(List.of(t("systemBrandingView.source.mounted") + "|success",
                        t("systemBrandingView.source.local") + "|success",
                        t("systemBrandingView.source.embedded") + "|contrast",
                        t("systemBrandingView.source.absent") + "|error",
                        t("systemBrandingView.source.unreadable") + "|error"),
                componentColumn(assets, 2).stream().map(PlainSmallViewTestSupport::badge).toList());
        assertEquals(List.of("/brand/theme.css", "brand/colors.css", "classpath", "-", "permission denied"),
                column(assets, 3));
    }

    @Test
    void given_openView_when_reloadClicked_then_cacheIsDiscardedAndTheReportReRendered() {
        init();
        VerticalLayout body = (VerticalLayout) view.getChildren().toList().get(3);
        Grid<?> before = grid(SystemBrandingView.SUMMARY_GRID_ID);
        report = new BrandReport("/brand", "brand", false, false, null, null, null, null, IN_USE, List.of());

        test(byId(view, SystemBrandingView.RELOAD_BUTTON_ID, Button.class)).click();

        assertEquals(1, reloads);
        assertEquals(6, body.getComponentCount(), "the body is replaced, not appended to");
        Grid<SystemBrandingView.Row> after = grid(SystemBrandingView.SUMMARY_GRID_ID);
        assertFalse(before == after, "a fresh summary grid is rendered");
        assertEquals(t("systemBrandingView.summary.embedded", "/brand"), column(after, 1).get(7));
        Notification n = $(Notification.class).single();
        assertEquals(t("systemBrandingView.reloaded"), test(n).getText());
        assertEquals(3000, n.getDuration());
        assertSame(Notification.Position.MIDDLE, n.getPosition());
    }
}
