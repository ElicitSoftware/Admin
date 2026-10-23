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

import com.elicitsoftware.admin.i18n.ElicitI18NProvider;
import com.elicitsoftware.admin.i18n.LanguageSwitcher;
import com.elicitsoftware.admin.i18n.LocaleSelection;
import com.elicitsoftware.admin.manual.AdminManual;
import com.elicitsoftware.admin.util.BrandUtil;
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ScrollIntoViewOption;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-001: Authenticate and Access the Admin Console, UC-019 (BR-076 to BR-078), UC-021 (BR-087),
 * UC-026, UC-028 (BR-110 to BR-114) and UC-029 (BR-006) — {@link MainLayout} built with
 * {@code new} in a plain browserless UI, its injected beans replaced by stand-ins: the role-aware
 * drawer, the branded header with its manual link and language selector, the banners shown above
 * the routed view, and the blocking no-department dialog.
 */
class MainLayoutUnitTest extends PlainAdminViewTestSupport {

    private Login login;
    private Presence presence;
    private boolean manualAvailable;
    private BrandUtil.BrandInfo brand;
    private List<String> seeded;
    private int defaultAccountCalls;

    @BeforeEach
    void stubs() {
        login = new Login();
        presence = new Presence();
        manualAvailable = false;
        brand = new BrandUtil.BrandInfo(BrandUtil.BrandInfo.DEFAULT_BRAND_KEY, "Elicit", "/brand/logo.png", "");
        seeded = List.of();
        defaultAccountCalls = 0;
    }

    private MainLayout build(String... roles) {
        MainLayout layout = new MainLayout();
        layout.uiSessionLogin = login(login);
        layout.identity = identity("someone", roles);
        layout.brandUtil = new BrandUtil() {
            @Override
            public BrandInfo detectCurrentBrand() {
                return brand;
            }

            @Override
            public String getIconResourcePath(BrandInfo brandInfo) {
                return "/icons/" + brandInfo.getBrandKey() + ".png";
            }
        };
        layout.localeSelection = new LocaleSelection();
        layout.i18nProvider = new ElicitI18NProvider();
        layout.surveyPresence = presence(presence);
        layout.defaultAccounts = new DefaultAccountCheck() {
            @Override
            public List<String> findDefaultAccounts() {
                defaultAccountCalls++;
                return seeded;
            }
        };
        layout.manual = new AdminManual() {
            @Override
            public boolean isAvailable() {
                return manualAvailable;
            }
        };
        layout.init();
        return layout;
    }

    private static User withDepartment() {
        return user("someone", department(1, "Oncology"));
    }

    private SideNav nav(MainLayout layout) {
        return find(SideNav.class, layout).single();
    }

    private static List<String> labels(List<SideNavItem> items) {
        return items.stream().map(SideNavItem::getLabel).toList();
    }

    private static String icon(Component component) {
        return assertInstanceOf(Icon.class, component).getElement().getAttribute("icon");
    }

    private Div header(MainLayout layout) {
        return find(Div.class, layout).all().stream()
                .filter(d -> d.hasClassName("branded-header")).findFirst().orElseThrow();
    }

    private List<Dialog> openDialogs() {
        return $(Dialog.class).all().stream().filter(Dialog::isOpened).toList();
    }

    // ---- drawer (UC-001, UC-029 BR-006) ----------------------------------------------------------

    @Test
    void given_noConsoleRecord_when_init_then_theDrawerOffersOnlyLogout() {
        MainLayout layout = build("elicit_admin");

        assertSame(null, layout.user);
        List<SideNavItem> items = nav(layout).getItems();
        assertEquals(List.of(t("mainLayout.nav.logout")), labels(items));
        assertEquals("logout", items.get(0).getPath());
        assertEquals("vaadin:lock", icon(items.get(0).getPrefixComponent()));
    }

    @Test
    void given_userWithoutAdminRoleAndNoManual_when_init_then_theDrawerHoldsTheEverydayItemsOnly() {
        login.user = withDepartment();

        MainLayout layout = build("elicit_user");

        assertSame(login.user, layout.user);
        List<SideNavItem> items = nav(layout).getItems();
        assertEquals(List.of(t("mainLayout.nav.searchSubjects"), t("mainLayout.nav.registerSubjects"),
                t("mainLayout.nav.participation"), t("mainLayout.nav.logout")), labels(items));
        assertEquals(List.of("search", "register-view", "participation", "logout"),
                items.stream().map(SideNavItem::getPath).toList());
        assertEquals(List.of("vaadin:search", "vaadin:users", "vaadin:chart", "vaadin:lock"),
                items.stream().map(i -> icon(i.getPrefixComponent())).toList());
    }

    @Test
    void given_userWithoutAdminRoleAndAManual_when_init_then_theManualSitsBeforeLogout() {
        login.user = withDepartment();
        manualAvailable = true;

        MainLayout layout = build("elicit_user");

        List<SideNavItem> items = nav(layout).getItems();
        assertEquals(List.of(t("mainLayout.nav.searchSubjects"), t("mainLayout.nav.registerSubjects"),
                t("mainLayout.nav.participation"), t("mainLayout.nav.manual"), t("mainLayout.nav.logout")), labels(items));
        SideNavItem manual = items.get(3);
        assertEquals("api/manual", manual.getPath());
        assertTrue(manual.isRouterIgnore(), "the router must not swallow /api/manual");
        assertTrue(manual.isOpenInNewBrowserTab());
        assertEquals("vaadin:file-text", icon(manual.getPrefixComponent()));
    }

    @Test
    void given_administrator_when_init_then_theAdminAndSystemSectionsListEveryEntryInOrder() {
        login.user = withDepartment();

        MainLayout layout = build("elicit_admin");

        List<SideNavItem> items = nav(layout).getItems();
        assertEquals(List.of(t("mainLayout.nav.searchSubjects"), t("mainLayout.nav.registerSubjects"),
                t("mainLayout.nav.participation"), t("mainLayout.nav.admin"), t("mainLayout.nav.system"),
                t("mainLayout.nav.logout")), labels(items));

        SideNavItem admin = items.get(3);
        assertEquals("vaadin:cog", icon(admin.getPrefixComponent()));
        assertEquals(List.of(t("mainLayout.nav.departments"), t("mainLayout.nav.messageTemplates"),
                t("mainLayout.nav.users"), t("mainLayout.nav.importRespondent"),
                t("mainLayout.nav.applySurveyDefinition"), t("mainLayout.nav.exportSurveyDefinition")),
                labels(admin.getItems()));
        assertEquals(List.of("departments", "message-templates", "users", "respondent-import",
                "apply-survey-definition", "export-survey-definition"),
                admin.getItems().stream().map(SideNavItem::getPath).toList());
        assertEquals(List.of("vaadin:grid", "vaadin:envelope", "vaadin:group", "vaadin:upload",
                "vaadin:file-process", "vaadin:download"),
                admin.getItems().stream().map(i -> icon(i.getPrefixComponent())).toList());

        SideNavItem system = items.get(4);
        assertEquals("vaadin:tools", icon(system.getPrefixComponent()));
        assertEquals(List.of(t("mainLayout.nav.systemOverview"), t("mainLayout.nav.systemDatabase"),
                t("mainLayout.nav.systemBranding"), t("mainLayout.nav.systemEmail"),
                t("mainLayout.nav.systemConnections"), t("mainLayout.nav.systemOidc")), labels(system.getItems()));
        assertEquals(List.of("system", "system/database", "system/branding", "system/email",
                "system/connections", "debug"),
                system.getItems().stream().map(SideNavItem::getPath).toList());
        assertEquals(List.of("vaadin:dashboard", "vaadin:database", "vaadin:paintbrush", "vaadin:paperplane",
                "vaadin:connect", "vaadin:shield"),
                system.getItems().stream().map(i -> icon(i.getPrefixComponent())).toList());
    }

    // ---- header (UC-026, UC-029) -----------------------------------------------------------------

    @Test
    void given_defaultBrandAndNoManual_when_init_then_theHeaderHoldsToggleLogoTitleAndLanguageSelector() {
        MainLayout layout = build("elicit_user");

        Div header = header(layout);
        assertEquals("navbar", header.getElement().getAttribute("slot"));
        assertEquals(List.of("branded-header"), new ArrayList<>(header.getClassNames()));
        List<Component> parts = header.getChildren().toList();
        assertEquals(4, parts.size(), "toggle, logo, title and language selector");
        assertInstanceOf(DrawerToggle.class, parts.get(0));

        Div logoContainer = assertInstanceOf(Div.class, parts.get(1));
        assertTrue(logoContainer.hasClassName("logo-container"));
        Image logo = assertInstanceOf(Image.class, logoContainer.getChildren().findFirst().orElseThrow());
        assertEquals("/icons/default-brand.png", logo.getSrc());
        assertEquals(t("common.logoAlt", "Elicit"), logo.getAlt().orElseThrow());
        assertTrue(logo.hasClassName("logo"));

        Anchor title = assertInstanceOf(Anchor.class, parts.get(2));
        assertEquals("/", title.getHref());
        assertEquals(t("common.appTitle.default", t("common.appType.admin")), title.getText());
        assertTrue(title.hasClassName("brand-title"));

        assertEquals(LanguageSwitcher.ID, parts.get(3).getId().orElseThrow());
    }

    @Test
    void given_aMountedBrandWithACssClass_when_init_then_theHeaderCarriesItsClassAndName() {
        brand = new BrandUtil.BrandInfo("acme", "Acme Health", "/opt/brand/logo.png", "acme-brand");

        MainLayout layout = build("elicit_user");

        Div header = header(layout);
        assertTrue(header.hasClassName("acme-brand"));
        Anchor title = header.getChildren().filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .filter(a -> a.hasClassName("brand-title")).findFirst().orElseThrow();
        assertEquals(t("common.appTitle", "Acme Health", t("common.appType.admin")), title.getText());
        Image logo = find(Image.class, header).single();
        assertEquals("/icons/acme.png", logo.getSrc());
        assertEquals(t("common.logoAlt", "Acme Health"), logo.getAlt().orElseThrow());
    }

    @Test
    void given_aBrandWithNoCssClass_when_init_then_theHeaderKeepsOnlyItsOwnClass() {
        brand = new BrandUtil.BrandInfo("acme", "Acme Health", "/opt/brand/logo.png", null);

        MainLayout layout = build("elicit_user");

        assertEquals(List.of("branded-header"), new ArrayList<>(header(layout).getClassNames()));
    }

    @Test
    void given_noBrandDetected_when_init_then_theHeaderFallsBackToTheDefaultTitleWithoutALogo() {
        brand = null;

        MainLayout layout = build("elicit_user");

        Div header = header(layout);
        assertEquals(List.of("branded-header"), new ArrayList<>(header.getClassNames()));
        assertTrue(find(Image.class, header).all().isEmpty(), "no brand, no logo");
        Anchor title = find(Anchor.class, header).single();
        assertEquals(t("common.appTitle.default", t("common.appType.admin")), title.getText());
    }

    @Test
    void given_aPackagedManual_when_init_then_theHeaderLinksToItInANewTab() {
        manualAvailable = true;

        MainLayout layout = build("elicit_user");

        Anchor link = find(Anchor.class, header(layout)).all().stream()
                .filter(a -> a.hasClassName("header-manual-link")).findFirst().orElseThrow();
        assertEquals("/api/manual", link.getHref());
        assertEquals(t("mainLayout.header.manual"), link.getText());
        assertTrue(link.isRouterIgnore());
        assertEquals("_blank", link.getElement().getAttribute("target"));
        assertEquals(t("mainLayout.header.manualTitle"), link.getElement().getAttribute("title"));
        assertEquals("vaadin:file-text", link.getElement().getChild(0).getAttribute("icon"));
    }

    @Test
    void given_noPackagedManual_when_init_then_theHeaderOffersNoManualLink() {
        MainLayout layout = build("elicit_user");

        assertTrue(find(Anchor.class, header(layout)).all().stream().noneMatch(a -> a.hasClassName("header-manual-link")));
    }

    @Test
    void given_noManualBean_when_init_then_noManualIsOffered() {
        login.user = withDepartment();
        MainLayout layout = new MainLayout();
        layout.uiSessionLogin = login(login);
        layout.identity = identity("someone", "elicit_user");
        layout.brandUtil = new BrandUtil() {
            @Override
            public BrandInfo detectCurrentBrand() {
                return null;
            }
        };
        layout.localeSelection = new LocaleSelection();
        layout.i18nProvider = new ElicitI18NProvider();

        layout.init();

        assertFalse(labels(nav(layout).getItems()).contains(t("mainLayout.nav.manual")));
    }

    // ---- navigation (scroll to top) --------------------------------------------------------------

    @Test
    void given_attachedLayout_when_navigating_then_theContentIsScrolledIntoView() {
        login.user = withDepartment();
        MainLayout layout = build("elicit_user");
        int[] scrolls = {0};
        layout.setContent(new Div() {
            @Override
            public void scrollIntoView(ScrollIntoViewOption... options) {
                scrolls[0]++;
            }
        });
        UI.getCurrent().add(layout);

        UI.getCurrent().navigate("register");

        assertEquals(1, scrolls[0], "after navigation the content must be scrolled to the top");
    }

    @Test
    void given_layoutNotAttached_when_navigating_then_nothingIsScrolled() {
        login.user = withDepartment();
        MainLayout layout = build("elicit_user");
        int[] scrolls = {0};
        layout.setContent(new Div() {
            @Override
            public void scrollIntoView(ScrollIntoViewOption... options) {
                scrolls[0]++;
            }
        });

        UI.getCurrent().navigate("register");

        assertEquals(0, scrolls[0], "only an attached layout listens to navigation");
    }

    // ---- banners (UC-019, UC-021) ----------------------------------------------------------------

    @Test
    void given_surveyInstalledAndNoSeededAccounts_when_showingAView_then_theViewIsShownUnwrapped() {
        login.user = withDepartment();
        seeded = List.of();
        MainLayout layout = build("elicit_admin");
        Div view = new Div();

        layout.showRouterLayoutContent(view);

        assertSame(view, layout.getContent());
        assertEquals(1, presence.calls);
        assertEquals(1, defaultAccountCalls);
        assertTrue(openDialogs().isEmpty());
    }

    @Test
    void given_noSurveyInstalled_when_showingAViewToAUser_then_thePlainTextBannerPrecedesTheView() {
        login.user = withDepartment();
        presence.installed = false;
        MainLayout layout = build("elicit_user");
        Div view = new Div();

        layout.showRouterLayoutContent(view);

        VerticalLayout wrapper = assertInstanceOf(VerticalLayout.class, layout.getContent());
        assertTrue(wrapper.hasClassName("console-notices"));
        assertEquals("100%", wrapper.getWidth());
        assertEquals("100%", wrapper.getHeight());
        assertFalse(wrapper.isPadding());
        assertFalse(wrapper.isSpacing());
        List<Component> children = wrapper.getChildren().toList();
        assertEquals(2, children.size());
        assertEquals(MissingSurveyNotice.BANNER_ID, children.get(0).getId().orElseThrow());
        assertTrue(find(RouterLink.class, children.get(0)).all().isEmpty(),
                "a user cannot apply a definition, so no link is offered (BR-078)");
        assertSame(view, children.get(1));
        assertEquals(0, defaultAccountCalls, "only an administrator is warned about seeded accounts");
    }

    @Test
    void given_noSurveyInstalled_when_showingAViewToAnAdministrator_then_theBannerLinksToTheRemedy() {
        login.user = withDepartment();
        presence.installed = false;
        MainLayout layout = build("elicit_admin");
        Div view = new Div();

        layout.showRouterLayoutContent(view);

        VerticalLayout wrapper = assertInstanceOf(VerticalLayout.class, layout.getContent());
        Component banner = wrapper.getChildren().findFirst().orElseThrow();
        assertEquals(MissingSurveyNotice.BANNER_ID, banner.getId().orElseThrow());
        assertEquals(1, find(RouterLink.class, banner).all().size(), "an administrator is linked to the remedy (BR-078)");
    }

    @Test
    void given_seededAccountsRemain_when_showingAViewToAnAdministrator_then_theAccountBannerPrecedesTheView() {
        login.user = withDepartment();
        seeded = List.of("admin");
        MainLayout layout = build("elicit_admin");
        Div view = new Div();

        layout.showRouterLayoutContent(view);

        VerticalLayout wrapper = assertInstanceOf(VerticalLayout.class, layout.getContent());
        List<Component> children = wrapper.getChildren().toList();
        assertEquals(2, children.size());
        assertEquals(DefaultAccountNotice.BANNER_ID, children.get(0).getId().orElseThrow());
        assertSame(view, children.get(1));
    }

    @Test
    void given_noSurveyAndSeededAccounts_when_showingAViewToAnAdministrator_then_bothBannersPrecedeTheView() {
        login.user = withDepartment();
        presence.installed = false;
        seeded = List.of("admin", "user");
        MainLayout layout = build("elicit_admin");
        Div view = new Div();

        layout.showRouterLayoutContent(view);

        List<Component> children = assertInstanceOf(VerticalLayout.class, layout.getContent()).getChildren().toList();
        assertEquals(3, children.size());
        assertEquals(MissingSurveyNotice.BANNER_ID, children.get(0).getId().orElseThrow());
        assertEquals(DefaultAccountNotice.BANNER_ID, children.get(1).getId().orElseThrow());
        assertSame(view, children.get(2));
    }

    @Test
    void given_seededAccountsRemain_when_showingAViewToAUser_then_noAccountBannerIsShown() {
        login.user = withDepartment();
        seeded = List.of("admin");
        MainLayout layout = build("elicit_user");
        Div view = new Div();

        layout.showRouterLayoutContent(view);

        assertSame(view, layout.getContent());
    }

    // ---- department gate (UC-028) ----------------------------------------------------------------

    @Test
    void given_userWithDepartments_when_showingAView_then_noDialogAndNoReRead() {
        login.user = withDepartment();
        MainLayout layout = build("elicit_user");

        layout.showRouterLayoutContent(new Div());

        assertTrue(openDialogs().isEmpty());
        assertEquals(0, login.refreshCalls, "the session record shows a department, so nothing is re-read");
    }

    @Test
    void given_noConsoleRecord_when_showingAView_then_noDialogIsOpened() {
        MainLayout layout = build("elicit_user");

        layout.showRouterLayoutContent(new Div());

        assertTrue(openDialogs().isEmpty(), "UC-001 handles a missing record; the dialog would mislead");
        assertEquals(0, login.refreshCalls);
    }

    @Test
    void given_userWithoutDepartment_when_showingAView_then_theUserVariantOfTheDialogOpens() {
        login.user = user("someone");
        MainLayout layout = build("elicit_user");

        layout.showRouterLayoutContent(new Div());

        assertEquals(1, login.refreshCalls, "a session copy without a department is re-read (BR-114)");
        List<Dialog> dialogs = openDialogs();
        assertEquals(1, dialogs.size());
        Dialog dialog = dialogs.get(0);
        assertEquals(MissingDepartmentDialog.DIALOG_ID, dialog.getId().orElseThrow());
        assertTrue(find(Component.class, dialog).all().stream()
                .noneMatch(c -> c.getId().filter(MissingDepartmentDialog.ADD_BUTTON_ID::equals).isPresent()));
        assertTrue(find(Component.class, dialog).all().stream()
                .noneMatch(c -> c.getId().filter(MissingDepartmentDialog.MANUAL_LINK_ID::equals).isPresent()));
    }

    @Test
    void given_administratorWithoutDepartment_when_showingAView_then_theAdministratorVariantOpens() {
        login.user = user("someone");
        manualAvailable = true;
        MainLayout layout = build("elicit_admin");

        layout.showRouterLayoutContent(new Div());

        Dialog dialog = openDialogs().get(0);
        assertTrue(find(Component.class, dialog).all().stream()
                .anyMatch(c -> c.getId().filter(MissingDepartmentDialog.ADD_BUTTON_ID::equals).isPresent()),
                "an administrator is offered Add a department");
        assertTrue(find(Component.class, dialog).all().stream()
                .anyMatch(c -> c.getId().filter(MissingDepartmentDialog.MANUAL_LINK_ID::equals).isPresent()),
                "the packaged manual is offered in the dialog");
    }

    @Test
    void given_theDialogIsOpen_when_navigatingAgain_then_theSameOverlayIsReused() {
        login.user = user("someone");
        MainLayout layout = build("elicit_user");

        layout.showRouterLayoutContent(new Div());
        layout.showRouterLayoutContent(new Div());

        assertEquals(1, $(Dialog.class).all().size(), "one overlay, not one per navigation");
        assertEquals(2, login.refreshCalls);
    }

    @Test
    void given_theDialogWasClosedElsewhere_when_navigatingAgain_then_aFreshOneOpens() {
        login.user = user("someone");
        MainLayout layout = build("elicit_user");
        layout.showRouterLayoutContent(new Div());
        Dialog first = openDialogs().get(0);
        first.close();

        layout.showRouterLayoutContent(new Div());

        List<Dialog> open = openDialogs();
        assertEquals(1, open.size());
        assertFalse(open.get(0) == first, "a closed dialog is replaced, not reused");
    }

    @Test
    void given_aDepartmentAssignedSinceSignIn_when_showingAView_then_theReReadClearsTheCondition() {
        login.user = user("someone");
        login.replaceOnRefresh = true;
        login.afterRefresh = withDepartment();
        MainLayout layout = build("elicit_user");

        layout.showRouterLayoutContent(new Div());

        assertEquals(1, login.refreshCalls);
        assertTrue(openDialogs().isEmpty());
    }

    @Test
    void given_theRecordVanishesOnReRead_when_showingAView_then_noDialogIsOpened() {
        login.user = user("someone");
        login.replaceOnRefresh = true;
        login.afterRefresh = null;
        MainLayout layout = build("elicit_user");

        layout.showRouterLayoutContent(new Div());

        assertTrue(openDialogs().isEmpty());
    }

    @Test
    void given_theDialogIsOpen_when_aDepartmentIsAssigned_then_theNextScreenClosesIt() {
        login.user = user("someone");
        MainLayout layout = build("elicit_user");
        layout.showRouterLayoutContent(new Div());
        Dialog dialog = openDialogs().get(0);

        login.user = withDepartment();
        layout.showRouterLayoutContent(new Div());

        assertFalse(dialog.isOpened(), "BR-114: the condition is re-established on every screen");
        assertTrue(openDialogs().isEmpty());
    }

    @Test
    void given_administratorWithoutDepartment_when_onEditDepartment_then_theRemedyScreenIsNotBlocked() {
        login.user = user("someone");
        MainLayout layout = build("elicit_admin");
        layout.showRouterLayoutContent(new Div());
        Dialog dialog = openDialogs().get(0);

        layout.showRouterLayoutContent(new EditDepartmentView());

        assertFalse(dialog.isOpened(), "BR-111: the blocking notice must not block its own remedy");
        assertTrue(openDialogs().isEmpty());
        assertEquals(1, login.refreshCalls, "on the remedy screen the record is not even re-read");
    }

    @Test
    void given_userWithoutDepartment_when_onEditDepartment_then_theDialogStillBlocks() {
        login.user = user("someone");
        MainLayout layout = build("elicit_user");

        layout.showRouterLayoutContent(new EditDepartmentView());

        assertEquals(1, openDialogs().size(), "only an administrator can carry out the remedy");
        assertNotNull(openDialogs().get(0));
    }
}
