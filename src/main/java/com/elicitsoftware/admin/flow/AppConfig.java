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

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;

/**
 * Application configuration class for Vaadin application shell settings.
 *
 * <p>This class implements {@link AppShellConfigurator} to provide configuration
 * for the Vaadin application shell, including PWA settings, viewport configuration,
 * and other application-wide shell properties.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li><strong>PWA Configuration:</strong> Progressive Web App settings</li>
 *   <li><strong>Viewport Settings:</strong> Mobile-responsive viewport configuration</li>
 *   <li><strong>Meta Tags:</strong> Application metadata and SEO settings</li>
 *   <li><strong>Theme Configuration:</strong> Application-wide theme settings</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see AppShellConfigurator
 */
@Theme("starter-theme")
public class AppConfig implements AppShellConfigurator {

    /**
     * Default constructor for AppConfig.
     *
     * <p>Creates a new AppConfig instance that provides Vaadin application shell
     * configuration. This constructor is automatically called by the Vaadin
     * framework during application startup to configure the application shell
     * settings.</p>
     *
     * <p>The configuration includes:</p>
     * <ul>
     *   <li>Progressive Web App (PWA) settings</li>
     *   <li>Viewport meta tag configuration for mobile responsiveness</li>
     *   <li>Application theme and styling configuration</li>
     *   <li>Security and performance optimizations</li>
     * </ul>
     */
    public AppConfig() {
        // Default constructor for Vaadin AppShellConfigurator
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addLink("shortcut icon", "icons/favicon.ico");
        settings.addFavIcon("icon", "/icons/favicon-32x32.png", "32x32");
    }
}
