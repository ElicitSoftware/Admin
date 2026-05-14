package com.elicitsoftware.service;

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

import com.elicitsoftware.admin.flow.AppConfig;
import com.elicitsoftware.admin.flow.DebugView;
import com.elicitsoftware.admin.flow.MainLayout;
import com.elicitsoftware.admin.flow.RegisterView;
import com.elicitsoftware.admin.flow.SearchView;
import com.elicitsoftware.admin.flow.LogoutView;
import com.elicitsoftware.admin.flow.UiSessionLogin;
import com.elicitsoftware.admin.util.BrandUtil;
import com.elicitsoftware.model.User;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import io.quarkus.arc.Arc;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CDI-aware tests for views that use {@code @PostConstruct} with {@code @Inject}
 * dependencies.  Karibu-Testing creates views via {@code new ClassName()}, which
 * bypasses CDI and skips {@code @PostConstruct}.  These tests obtain view
 * instances through {@code Arc.container().instance()} so that CDI injects
 * dependencies and calls {@code @PostConstruct init()} before the assertions run.
 *
 *
 * <p>MockVaadin is set up in {@code @BeforeEach} so that {@code UI.getCurrent()}
 * and {@code VaadinSession.getCurrent()} return non-null inside
 * {@code @PostConstruct} methods.</p>
 *
 * <p>{@code @InjectMock} is intentionally avoided for {@code UiSessionLogin}
 * because that bean is {@code @UIScoped}: the Vaadin CDI scope context is not
 * activated by MockVaadin, so {@code CreateMockitoMocksCallback.getBeanHandle()}
 * throws a {@code NullPointerException} during test-class construction.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgreSQLTestResource.class)
class AdminViewCDITest {

    private SecurityIdentity mockIdentity;
    private UiSessionLogin mockSessionLogin;

    @BeforeEach
    void setUp() {
        mockIdentity = Mockito.mock(SecurityIdentity.class);
        Principal principal = () -> "testuser";
        Mockito.when(mockIdentity.getPrincipal()).thenReturn(principal);
        Mockito.when(mockIdentity.isAnonymous()).thenReturn(false);
        Mockito.when(mockIdentity.getRoles()).thenReturn(Set.of("elicit_user"));
        Mockito.when(mockIdentity.hasRole(Mockito.anyString())).thenReturn(false);

        mockSessionLogin = Mockito.mock(UiSessionLogin.class);
        Mockito.when(mockSessionLogin.getUser()).thenReturn(null);

        Routes routes = new Routes(Set.of(SearchView.class, LogoutView.class), Set.of(), true);
        MockVaadin.setup(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
    }

    // -----------------------------------------------------------------------
    // Helper: set a (possibly private/inherited) field by name via reflection
    // -----------------------------------------------------------------------

    private static void setField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + target.getClass());
    }

    private static void invokeInit(Object target) throws ReflectiveOperationException {
        Method m = target.getClass().getDeclaredMethod("init");
        m.setAccessible(true);
        m.invoke(target);
    }

    // -----------------------------------------------------------------------
    // AppConfig - @ApplicationScoped @Startup; already ran at Quarkus start
    // -----------------------------------------------------------------------

    @Test
    void appConfig_isAvailableAsSingleton() {
        AppConfig config = Arc.container().instance(AppConfig.class).get();
        assertNotNull(config, "AppConfig singleton should be available via Arc");
    }

    // -----------------------------------------------------------------------
    // SearchView - null-user branch of @PostConstruct init()
    // -----------------------------------------------------------------------

    @Test
    void searchViewInit_nullUser_doesNotThrow() {
        SearchView view = new SearchView();
        assertDoesNotThrow(() -> {
            setField(view, "identity", mockIdentity);
            setField(view, "uiSessionLogin", mockSessionLogin);
            invokeInit(view);
        }, "SearchView.init() with null user should not throw");
        assertNotNull(view);
    }

    // -----------------------------------------------------------------------
    // MainLayout - null-user branch of @PostConstruct init()
    // -----------------------------------------------------------------------

    @Test
    void mainLayoutInit_nullUser_doesNotThrow() {
        BrandUtil brandUtil = Arc.container().instance(BrandUtil.class).get();
        MainLayout layout = new MainLayout();
        assertDoesNotThrow(() -> {
            setField(layout, "uiSessionLogin", mockSessionLogin);
            setField(layout, "identity", mockIdentity);
            setField(layout, "brandUtil", brandUtil);
            invokeInit(layout);
        }, "MainLayout.init() with null user should not throw");
        assertNotNull(layout);
    }

    // -----------------------------------------------------------------------
    // RegisterView - @PostConstruct init() with a user having empty departments
    // -----------------------------------------------------------------------

    @Test
    void registerViewInit_emptyDepartments_doesNotThrow() {
        User fakeUser = new User();
        fakeUser.setDepartments(new HashSet<>());
        Mockito.when(mockSessionLogin.getUser()).thenReturn(fakeUser);

        RegisterView view = new RegisterView();
        assertDoesNotThrow(() -> {
            setField(view, "uiSessionLogin", mockSessionLogin);
            invokeInit(view);
        }, "RegisterView.init() with empty-departments user should not throw");
        assertNotNull(view);
    }

    // -----------------------------------------------------------------------
    // DebugView - @PostConstruct init() appends identity + token info
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void debugViewInit_rendersDebugInfo() {
        Instance<JsonWebToken> mockIdToken = Mockito.mock(Instance.class);
        Mockito.when(mockIdToken.isResolvable()).thenReturn(false);

        Instance<AccessTokenCredential> mockAccess = Mockito.mock(Instance.class);
        Mockito.when(mockAccess.isResolvable()).thenReturn(false);

        DebugView view = new DebugView();
        assertDoesNotThrow(() -> {
            setField(view, "identity", mockIdentity);
            setField(view, "idTokenInstance", mockIdToken);
            setField(view, "accessTokenInstance", mockAccess);
            invokeInit(view);
        }, "DebugView.init() should not throw");
        assertNotNull(view);
    }
}
