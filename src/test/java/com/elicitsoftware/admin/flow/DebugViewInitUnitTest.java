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

import com.elicitsoftware.security.RoleSecurityIdentityAugmentor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Pre;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.security.Principal;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: View Security Diagnostics — the page {@link DebugView#init()} builds, in a plain
 * browserless UI with stand-ins for the identity and both tokens. Complements
 * {@link DebugViewTest} (the masking helper): covers the identity facts, the role source, the
 * tokens masked unless revealing is configured, and the wording when a token is unavailable or
 * fails to resolve.
 */
class DebugViewInitUnitTest extends PlainSmallViewTestSupport {

    private static final String ID_TOKEN = "eyJ.id.tokenWXYZ";
    private static final String ACCESS_TOKEN = "eyJ.access.tokenABCD";

    private static SecurityIdentity admin() {
        Principal principal = () -> "ada";
        return (SecurityIdentity) Proxy.newProxyInstance(DebugViewInitUnitTest.class.getClassLoader(),
                new Class<?>[]{SecurityIdentity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPrincipal" -> principal;
                    case "isAnonymous" -> false;
                    case "getRoles" -> Set.of("elicit_admin");
                    case "getAttribute" -> RoleSecurityIdentityAugmentor.ROLE_SOURCE_ATTRIBUTE.equals(args[0]) ? "oidc" : null;
                    case "hasRole" -> "elicit_admin".equals(args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> instance(boolean resolvable, Supplier<T> value) {
        return (Instance<T>) Proxy.newProxyInstance(DebugViewInitUnitTest.class.getClassLoader(),
                new Class<?>[]{Instance.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isResolvable" -> resolvable;
                    case "get" -> value.get();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static JsonWebToken idToken() {
        return (JsonWebToken) Proxy.newProxyInstance(DebugViewInitUnitTest.class.getClassLoader(),
                new Class<?>[]{JsonWebToken.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getRawToken")) {
                        return ID_TOKEN;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static DebugView view(Instance<JsonWebToken> id, Instance<AccessTokenCredential> access, boolean reveal) {
        DebugView view = new DebugView();
        view.identity = admin();
        view.idTokenInstance = id;
        view.accessTokenInstance = access;
        view.revealTokens = reveal;
        view.init();
        return view;
    }

    private static String identityLines() {
        return t("debugView.user", "ada") + "\n"
                + t("debugView.isAnonymous", false) + "\n"
                + t("debugView.roles", Set.of("elicit_admin")) + "\n"
                + t("debugView.roleSource", "oidc") + "\n"
                + t("debugView.hasAdminRole", true) + "\n"
                + t("debugView.hasUserRole", false) + "\n\n";
    }

    private static Pre info(DebugView view) {
        return (Pre) view.getComponentAt(1);
    }

    @Test
    void given_bothTokens_when_opened_then_identityIsListedAndTokensAreMasked() {
        DebugView view = view(instance(true, DebugViewInitUnitTest::idToken),
                instance(true, () -> new AccessTokenCredential(ACCESS_TOKEN)), false);

        assertEquals(2, view.getComponentCount());
        assertEquals(t("debugView.title"), ((H1) view.getComponentAt(0)).getText());
        Pre info = info(view);
        assertTrue(info.hasClassName("debug-info"));
        assertEquals(identityLines()
                + t("debugView.idToken", DebugView.maskToken(ID_TOKEN, false)) + "\n\n"
                + t("debugView.accessToken", DebugView.maskToken(ACCESS_TOKEN, false)) + "\n\n", info.getText());
        assertEquals(t("debugView.pageTitle"), view.getPageTitle());
    }

    @Test
    void given_revealConfigured_when_opened_then_rawTokensAreShown() {
        DebugView view = view(instance(true, DebugViewInitUnitTest::idToken),
                instance(true, () -> new AccessTokenCredential(ACCESS_TOKEN)), true);

        assertEquals(identityLines() + t("debugView.idToken", ID_TOKEN) + "\n\n"
                + t("debugView.accessToken", ACCESS_TOKEN) + "\n\n", info(view).getText());
    }

    @Test
    void given_noTokens_when_opened_then_eachIsReportedUnavailable() {
        DebugView view = view(instance(false, () -> null), instance(false, () -> null), false);

        assertEquals(identityLines() + t("debugView.idTokenUnavailable") + "\n\n"
                + t("debugView.accessTokenUnavailable") + "\n\n", info(view).getText());
    }

    @Test
    void given_tokensFailToResolve_when_opened_then_eachErrorIsReported() {
        DebugView view = view(instance(true, () -> {
            throw new IllegalStateException("no id");
        }), instance(true, () -> {
            throw new IllegalStateException("no access");
        }), false);

        assertEquals(identityLines() + t("debugView.idTokenError", "no id") + "\n\n"
                + t("debugView.accessTokenError", "no access") + "\n\n", info(view).getText());
    }
}
