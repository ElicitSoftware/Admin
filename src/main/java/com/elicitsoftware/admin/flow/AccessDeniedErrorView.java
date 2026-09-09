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

import com.vaadin.flow.router.AccessDeniedException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import jakarta.annotation.security.PermitAll;

/**
 * Overrides Vaadin's built-in {@code RouteAccessDeniedError} so that when
 * {@link com.vaadin.flow.server.auth.NavigationAccessControl} denies an
 * authenticated user access to a {@code @RolesAllowed}-guarded view, the
 * user sees the existing "Access Restricted" UI (from {@link UnauthorizedView})
 * instead of Vaadin's default reroute to a generic 404 page.
 * <p>
 * {@code @PermitAll} is declared explicitly here rather than relying on Vaadin's
 * {@code AccessAnnotationChecker} walking up to the inherited annotation on
 * {@link UnauthorizedView} - correct either way today, but an explicit annotation keeps
 * this view reachable even if {@code UnauthorizedView}'s own annotation ever changes.
 */
@PermitAll
public class AccessDeniedErrorView extends UnauthorizedView
        implements HasErrorParameter<AccessDeniedException> {

    /** Creates the view, delegating to {@link UnauthorizedView}'s default layout. */
    public AccessDeniedErrorView() {
    }

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<AccessDeniedException> parameter) {
        return 403;
    }
}
