package com.elicitsoftware.admin.manual;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;

/**
 * The administrator's manual as it ships inside the application (UC-029 BR-004).
 * <p>
 * The PDF is produced by {@code docs/manual/build-manual.sh} during the image build and lands on
 * the classpath at {@value #DEFAULT_RESOURCE}, stamped with the version and build date of the
 * build that produced it (UC-029 BR-003). It is deliberately <em>not</em> under
 * {@code META-INF/resources}, which Quarkus would serve without a role check; it reaches the
 * browser only through {@link ManualResource}, which requires a console role.
 * <p>
 * A build that did not produce it simply has no manual: {@link #isAvailable()} is false and the
 * navigation leaves the entry out (UC-029 A1). The application never fetches the manual from
 * anywhere outside its own image (UC-029 BR-004).
 */
@ApplicationScoped
public class AdminManual {

    /** Where {@code build-manual.sh} puts the PDF, relative to the classpath root. */
    static final String DEFAULT_RESOURCE = "manual/elicit-admin-manual.pdf";

    /** What the browser calls the file when the reader saves it (UC-029 A2). */
    public static final String FILE_NAME = "elicit-admin-manual.pdf";

    /** Overridable so a test can exercise the build that carries no manual (UC-029 A1). */
    @ConfigProperty(name = "admin.manual.resource", defaultValue = DEFAULT_RESOURCE)
    String resource;

    public AdminManual() {
        // CDI managed bean
    }

    /** Whether this build carries the manual. */
    public boolean isAvailable() {
        return loader().getResource(resource) != null;
    }

    /**
     * Opens the packaged manual.
     *
     * @return the PDF's bytes, or {@code null} when this build carries no manual
     */
    public InputStream open() {
        return loader().getResourceAsStream(resource);
    }

    private static ClassLoader loader() {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return contextLoader != null ? contextLoader : AdminManual.class.getClassLoader();
    }
}
