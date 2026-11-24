package com.elicitsoftware.test;

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
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Trusts only a documentation address (RFC 5737) as the reverse proxy, so the test client, which
 * connects from loopback, is a client the forwarding headers must not be taken from (NFR-026).
 */
public class UntrustedProxyTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.http.proxy.trusted-proxies", "203.0.113.7");
    }
}
