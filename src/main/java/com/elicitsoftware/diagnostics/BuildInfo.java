package com.elicitsoftware.diagnostics;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.admin.i18n.Translations;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What is running: name, version, build time, active profiles and uptime (UC-020 step 2).
 * <p>
 * The version and build time are Maven-filtered into {@code application.properties}
 * ({@code quarkus.application.version=@project.version@}, {@code build.timestamp=@timestamp@});
 * a build that skips filtering leaves them unset and the screen shows "unknown" (UC-020 A3).
 */
@Startup
@ApplicationScoped
public class BuildInfo {


    @ConfigProperty(name = "quarkus.application.name", defaultValue = "elicit-admin")
    String applicationName;

    @ConfigProperty(name = "quarkus.application.version")
    Optional<String> version;

    @ConfigProperty(name = "build.timestamp")
    Optional<String> buildTimestamp;

    private Instant startedAt;

    /** Creates the bean; CDI injects the configuration and records the start time. */
    public BuildInfo() {
        // CDI managed bean
    }

    @PostConstruct
    void recordStart() {
        startedAt = Instant.now();
    }

    /**
     * Returns {@code quarkus.application.name}.
     *
     * @return the application name, {@code elicit-admin} by default
     */
    public String applicationName() {
        return applicationName;
    }

    /**
     * The Maven project version, or "unknown" when the build did not record it.
     *
     * @return the version, or "unknown" in the administrator's language when it is unset, blank or
     *         an unfiltered {@code @...@} placeholder
     */
    public String version() {
        return version.filter(v -> !v.isBlank() && !v.startsWith("@")).orElse(Translations.get("system.build.unknown"));
    }

    /**
     * The Maven build timestamp, or "unknown" when the build did not record it.
     *
     * @return the build timestamp, or "unknown" in the administrator's language when it is unset,
     *         blank or an unfiltered {@code @...@} placeholder
     */
    public String buildTimestamp() {
        return buildTimestamp.filter(v -> !v.isBlank() && !v.startsWith("@")).orElse(Translations.get("system.build.unknown"));
    }

    /**
     * Returns the active configuration profiles.
     *
     * @return the active Quarkus profiles
     */
    public List<String> profiles() {
        return ConfigUtils.getProfiles();
    }

    /**
     * Returns when this bean was created at startup.
     *
     * @return the start time
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * Returns how long the application has run since {@link #startedAt()}.
     *
     * @return the time elapsed since startup
     */
    public Duration uptime() {
        return Duration.between(startedAt, Instant.now());
    }
}
