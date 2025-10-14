package com.elicitsoftware.config;

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

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS Application configuration for REST API endpoints.
 * <p>
 * This class configures the base path for all REST API endpoints in the application.
 * All JAX-RS resources will be available under the "/api" path prefix, allowing
 * proper separation between the Vaadin web interface and REST API endpoints.
 * </p>
 * 
 * <p>Configured endpoints:</p>
 * <ul>
 *   <li>/api/secured/* - TokenService endpoints with Bearer authentication (includes CSV import)</li>
 * </ul>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 */
@ApplicationPath("/api")
public class RestApplication extends Application {
    // Empty implementation - Quarkus will automatically discover and register
    // all JAX-RS resource classes under this application path
}