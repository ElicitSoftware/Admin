package com.elicitsoftware.diagnostics;

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

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Runs a block of a plain test against a configuration of the test's own making: the block's
 * thread gets a fresh context class loader with exactly {@code properties} registered for it,
 * so {@code ConfigProvider.getConfig()} inside the class under test sees those values and nothing
 * else — no {@code application.properties}, no environment, no system properties — and the
 * registration is released afterwards.
 */
final class IsolatedConfig {

    private IsolatedConfig() {
    }

    static <T> T with(Map<String, String> properties, Supplier<T> block) {
        return with(properties, null, block);
    }

    static <T> T with(Map<String, String> properties, String profile, Supplier<T> block) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder().addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(properties, "test", 500));
        if (profile != null) {
            builder.withProfile(profile);
        }
        Config config = builder.build();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        ClassLoader isolated = new ClassLoader(previous) {
        };
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();
        resolver.registerConfig(config, isolated);
        thread.setContextClassLoader(isolated);
        try {
            return block.get();
        } finally {
            thread.setContextClassLoader(previous);
            resolver.releaseConfig(config);
        }
    }
}
