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
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.internal.Routes;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.data.provider.DataChangeEvent;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationEvent;
import com.vaadin.flow.router.NavigationTrigger;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Base for the plain browserless tests of Admin's smaller views and dialogs: a Vaadin UI hosted
 * without Quarkus, CDI or a database, so that Pitest can mutate the code those tests reach
 * (UC-001, UC-006..008, UC-012, UC-013, UC-016, UC-018..020, UC-022..025, UC-028, UC-030).
 *
 * <p>The views are built with {@code new}; their injected fields are set to hand-written stand-ins
 * by the tests, and the {@code @PostConstruct} method is called by hand. Every stand-in that
 * subclasses a CDI bean is declared in the test itself, anonymously, so no scope annotation is
 * inherited by a class the booted tests' build would index.</p>
 */
abstract class PlainSmallViewTestSupport extends BrowserlessTest {

    static {
        // Copilot, a development tool, scans the whole classpath each time a mock VaadinService
        // starts -- about a second per test, which is what Pitest would pay per mutant.
        System.setProperty("vaadin.copilot.enable", "false");
    }

    /** The English bundle, read the way the application reads it. */
    private static final ElicitI18NProvider ENGLISH = new ElicitI18NProvider();

    private Locale previousDefault;

    /**
     * Every path a navigation was asked for, recorded before the target is built: a view whose
     * constructor queries the database never becomes the active view here, but the request to
     * enter it is what the code under test is responsible for.
     */
    final List<String> attemptedPaths = new ArrayList<>();

    @Override
    protected Routes discoverRoutes() {
        return new Routes(Set.of(), Set.of(), Set.of(), true);
    }

    /**
     * Registers the routes the views link or navigate to. The real view classes are registered by
     * path only: a link needs nothing but the class's route, and a navigation to a view whose
     * constructor queries the database is seen in {@link #attemptedPaths}. The landing page is a
     * placeholder, which carries no {@code @Route} because the booted tests' build indexes the
     * test classes too.
     */
    @BeforeEach
    void registerRoutesAndLocale() {
        previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().addBeforeLeaveListener(event -> attemptedPaths.add(event.getLocation().getPath()));
        RouteConfiguration routes = RouteConfiguration.forApplicationScope();
        routes.setRoute("", HomeStub.class);
        routes.setRoute("users", UsersView.class);
        routes.setRoute("departments", DepartmentsView.class);
        routes.setRoute("message-templates", MessageTemplatesView.class);
        routes.setRoute("survey-apply", SurveyDefinitionApplyView.class);
        routes.setRoute("system/database", SystemDatabaseView.class);
        routes.setRoute("system/branding", SystemBrandingView.class);
        routes.setRoute("system/email", SystemEmailView.class);
        routes.setRoute("system/connections", SystemConnectionsView.class);
        routes.setRoute("logout", LogoutView.class);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previousDefault);
    }

    /** The English text for a key, formatted as the views format it. */
    static String t(String key, Object... params) {
        String value = ENGLISH.getTranslation(key, Locale.ENGLISH, params);
        if (value.startsWith("!") && value.endsWith("!")) {
            throw new IllegalArgumentException("No English text for " + key);
        }
        return value;
    }

    /** A before-enter event for {@code target} with the given route parameters. */
    static BeforeEnterEvent beforeEnter(Class<? extends Component> target, Map<String, String> parameters) {
        UI ui = UI.getCurrent();
        NavigationEvent navigation = new NavigationEvent(ui.getInternals().getRouter(), new Location("x"), ui,
                NavigationTrigger.PROGRAMMATIC);
        return new BeforeEnterEvent(navigation, target, new RouteParameters(parameters), List.of());
    }

    /** The texts of every open notification, in the order they were shown. */
    List<String> notifications() {
        return $(Notification.class).all().stream().map(n -> test(n).getText()).toList();
    }

    /** A security identity whose principal is {@code name}; nothing else is answered. */
    static SecurityIdentity identity(String name) {
        Principal principal = () -> name;
        return (SecurityIdentity) Proxy.newProxyInstance(PlainSmallViewTestSupport.class.getClassLoader(),
                new Class<?>[]{SecurityIdentity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPrincipal" -> principal;
                    case "toString" -> "identity(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A badge as "text|tone", or a failure if the component is not a badge. */
    static String badge(Component component) {
        Span span = (Span) component;
        List<String> themes = new ArrayList<>(span.getElement().getThemeList());
        if (!themes.remove("badge") || themes.size() != 1) {
            throw new AssertionError("not a badge: " + span.getElement().getThemeList());
        }
        return span.getText() + "|" + themes.get(0);
    }

    /** Every cell text of a grid's column, top to bottom. */
    <T> List<String> column(Grid<T> grid, int columnIndex) {
        var tester = test(grid);
        List<String> texts = new ArrayList<>();
        for (int row = 0; row < tester.size(); row++) {
            texts.add(tester.getCellText(row, columnIndex));
        }
        return texts;
    }

    /** Every cell component of a grid's column, top to bottom. */
    <T> List<Component> componentColumn(Grid<T> grid, int columnIndex) {
        var tester = test(grid);
        List<Component> cells = new ArrayList<>();
        for (int row = 0; row < tester.size(); row++) {
            cells.add(tester.getCellComponent(row, columnIndex));
        }
        return cells;
    }

    /** A read-only summary grid: every row shown, compact, cells wrapped. */
    static void assertSummaryGrid(Grid<?> grid) {
        if (!grid.isAllRowsVisible()) {
            throw new AssertionError("all rows should be visible: " + grid.getId());
        }
        if (!grid.getThemeNames().contains(GridVariant.LUMO_COMPACT.getVariantName())
                || !grid.getThemeNames().contains(GridVariant.LUMO_WRAP_CELL_CONTENT.getVariantName())) {
            throw new AssertionError("compact, wrapping grid expected: " + grid.getThemeNames());
        }
    }

    /** Records each refresh a grid's data provider announces: "all" or the refreshed item. */
    static List<Object> refreshesOf(Grid<?> grid) {
        List<Object> refreshes = new ArrayList<>();
        grid.getDataProvider().addDataProviderListener(event -> refreshes.add(
                event instanceof DataChangeEvent.DataRefreshEvent<?> r ? r.getItem() : "all"));
        return refreshes;
    }

    /** Every header of a grid, left to right. */
    <T> List<String> headers(Grid<T> grid) {
        var tester = test(grid);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < grid.getColumns().size(); i++) {
            texts.add(tester.getHeaderCell(i));
        }
        return texts;
    }

    /** Finds the first descendant of a component with the given id. */
    static <T extends Component> T byId(Component root, String id, Class<T> type) {
        if (root.getId().filter(id::equals).isPresent() && type.isInstance(root)) {
            return type.cast(root);
        }
        for (Component child : root.getChildren().toList()) {
            T found = byId(child, id, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Placeholder for the landing page. */
    @AnonymousAllowed
    public static class HomeStub extends Div {
    }
}
