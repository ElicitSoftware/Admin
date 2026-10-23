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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.internal.Routes;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Base for the plain browserless tests of the large {@code admin.flow} views -- UC-001 and
 * UC-026 to UC-029 ({@link MainLayout}), UC-002 ({@link SearchView}), UC-003
 * ({@link RegisterView}) and UC-031 ({@link ParticipationView}): a Vaadin UI hosted by
 * {@link BrowserlessTest} with no Quarkus, no CDI and no database, the English bundle to compare
 * texts against, placeholder routes, and hand-written stand-ins for the injected beans.
 * <p>
 * The stand-ins for scoped CDI beans are anonymous subclasses, made by the factories below and
 * driven through plain holder objects: a named subclass would inherit the bean's scope and give
 * the booted tests two beans of one type.
 */
abstract class PlainAdminViewTestSupport extends BrowserlessTest {

    static {
        // Copilot, a development tool, scans the whole classpath each time a mock VaadinService
        // starts -- about a second per test, which is what Pitest would pay per mutant.
        System.setProperty("vaadin.copilot.enable", "false");
    }

    private static final Properties ENGLISH = bundle("/vaadin-i18n/translations.properties");
    private static final Properties SPANISH = bundle("/vaadin-i18n/translations_es_419.properties");

    private static Properties bundle(String resource) {
        Properties properties = new Properties();
        try (InputStream in = PlainAdminViewTestSupport.class.getResourceAsStream(resource);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    /**
     * The Latin American Spanish text for a key: for the labels whose English text equals the
     * value they translate, where only another language tells a translated value from a copied one.
     */
    static String spanish(String key) {
        String value = SPANISH.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("No Spanish text for " + key);
        }
        return value;
    }

    @Override
    protected Routes discoverRoutes() {
        return new Routes(Set.of(), Set.of(), Set.of(), true);
    }

    /**
     * Registers routes by path. The real view classes are registered only so that links to them
     * (side navigation items, router links) resolve; no test navigates to one. The placeholders
     * carry no {@code @Route}: the booted tests' build indexes the test classes too, and an
     * annotated placeholder would claim a real view's path.
     */
    @BeforeEach
    void registerRoutesAndLocale() {
        RouteConfiguration routes = RouteConfiguration.forApplicationScope();
        routes.setRoute("search", SearchView.class);
        routes.setRoute("register-view", RegisterView.class);
        routes.setRoute("participation", ParticipationView.class);
        routes.setRoute("departments", DepartmentsView.class);
        routes.setRoute("message-templates", MessageTemplatesView.class);
        routes.setRoute("users", UsersView.class);
        routes.setRoute("respondent-import", RespondentImportView.class);
        routes.setRoute("apply-survey-definition", SurveyDefinitionApplyView.class);
        routes.setRoute("export-survey-definition", SurveyDefinitionExportView.class);
        routes.setRoute("system", SystemOverviewView.class);
        routes.setRoute("system/database", SystemDatabaseView.class);
        routes.setRoute("system/branding", SystemBrandingView.class);
        routes.setRoute("system/email", SystemEmailView.class);
        routes.setRoute("system/connections", SystemConnectionsView.class);
        routes.setRoute("debug", DebugView.class);
        routes.setRoute("logout", LogoutView.class);
        routes.setRoute("", HomeStub.class);
        routes.setRoute("register", RegisterStub.class);
        UI.getCurrent().setLocale(Locale.ENGLISH);
    }

    /** The English bundle's text for a key, formatted as the views format it. */
    static String t(String key, Object... params) {
        String value = ENGLISH.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("No English text for " + key);
        }
        return params.length == 0 ? value : new MessageFormat(value, Locale.ENGLISH).format(params);
    }

    /** The path of the view the current UI last navigated to. */
    static String activePath() {
        return UI.getCurrent().getInternals().getActiveViewLocation().getPath();
    }

    /** The texts of every notification currently in the UI, in order. */
    List<String> notifications() {
        return $(Notification.class).all().stream().map(n -> test(n).getText()).toList();
    }

    static Department department(long id, String name) {
        Department department = new Department();
        department.id = id;
        department.name = name;
        return department;
    }

    static User user(String username, Department... departments) {
        User user = new User();
        user.setId(1);
        user.setUsername(username);
        user.setActive(true);
        user.setDepartments(new LinkedHashSet<>(List.of(departments)));
        return user;
    }

    /** A security identity for {@code name} holding exactly {@code roles}. */
    static SecurityIdentity identity(String name, String... roles) {
        Set<String> held = Set.of(roles);
        Principal principal = () -> name;
        return (SecurityIdentity) Proxy.newProxyInstance(SecurityIdentity.class.getClassLoader(),
                new Class<?>[] {SecurityIdentity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hasRole" -> held.contains((String) args[0]);
                    case "getRoles" -> held;
                    case "getPrincipal" -> principal;
                    case "isAnonymous" -> false;
                    case "toString" -> "identity " + name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** What the session-login stand-in answers, and how often it was asked to re-read. */
    static final class Login {
        User user;
        /** Replaces {@link #user} on refresh when set. */
        User afterRefresh;
        boolean replaceOnRefresh;
        int refreshCalls;
    }

    static UiSessionLogin login(Login state) {
        return new UiSessionLogin() {
            @Override
            public User getUser() {
                return state.user;
            }

            @Override
            public void refresh() {
                state.refreshCalls++;
                if (state.replaceOnRefresh) {
                    state.user = state.afterRefresh;
                }
            }
        };
    }

    /** Whether the survey-presence stand-in reports a survey, and how often it was asked. */
    static final class Presence {
        boolean installed = true;
        int calls;
    }

    static SurveyDefinitionPresenceCheck presence(Presence state) {
        return new SurveyDefinitionPresenceCheck() {
            @Override
            public boolean isSurveyInstalled() {
                state.calls++;
                return state.installed;
            }
        };
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

    /** Calls a private method that only a Panache-bound caller would otherwise reach. */
    @SuppressWarnings("unchecked")
    static <T> T invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads or replaces a private field that no package-private seam exposes. */
    @SuppressWarnings("unchecked")
    static <T> T field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * The placeholders are open to anyone: the application's NavigationAccessControl (registered
     * through META-INF/services, which the plain Vaadin environment also loads) would otherwise
     * reroute every navigation to them to the unregistered login view.
     */

    /** Placeholder route for the search page ({@code ""}). */
    @AnonymousAllowed
    public static class HomeStub extends Div {
    }

    /** Placeholder route for the register page, which the search grid's edit button opens. */
    @AnonymousAllowed
    public static class RegisterStub extends Div {
    }
}
