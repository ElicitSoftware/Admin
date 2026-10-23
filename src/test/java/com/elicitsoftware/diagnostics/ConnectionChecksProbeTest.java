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

import com.elicitsoftware.diagnostics.ConnectionChecks.Kind;
import com.elicitsoftware.diagnostics.ConnectionChecks.Target;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-025: Check Connections (BR-096, BR-097) — how one outbound dependency is probed: the
 * identity provider's discovery document must carry an issuer, any HTTP answer from a web target
 * counts as reachable, a socket target is connected to and closed, and every failure becomes a
 * DOWN or UNKNOWN result rather than an exception. The reachable targets are servers this test
 * starts on the loopback interface, on ports the operating system assigns; nothing leaves the
 * machine.
 */
class ConnectionChecksProbeTest {

    private final ConnectionChecks checks = new ConnectionChecks();
    private final List<String> requested = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        Thread.interrupted();
        if (server != null) {
            server.stop(0);
        }
    }

    /** Serves {@code status} and {@code body} at every path; returns the server's base address. */
    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 50);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Target target(String address, Kind kind) {
        return new Target("Identity provider", "quarkus.oidc.auth-server-url", address, kind);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    // ---- OIDC discovery ---------------------------------------------------------------------------

    @Test
    void given_aRealmServingDiscovery_when_check_then_upWithTheDocumentAddress() throws IOException {
        String base = serve(200, "{\"issuer\":\"http://idp/realms/elicit\"}");

        CheckResult result = checks.check(target(base + "/realms/elicit", Kind.OIDC_DISCOVERY));
        CheckResult slash = checks.check(target(base + "/realms/elicit/", Kind.OIDC_DISCOVERY));

        String uri = base + "/realms/elicit/.well-known/openid-configuration";
        assertEquals(CheckResult.Status.UP, result.status());
        assertEquals("quarkus.oidc.auth-server-url", result.name());
        assertEquals("discovery document served at " + uri, result.detail());
        assertEquals(result.detail(), slash.detail(), "a trailing slash is not doubled");
        assertEquals(List.of("GET /realms/elicit/.well-known/openid-configuration",
                "GET /realms/elicit/.well-known/openid-configuration"), requested);
        assertPlausible(result);
    }

    /** A probe takes well under a minute; an arithmetic slip in the timing would not. */
    private static void assertPlausible(CheckResult result) {
        assertTrue(result.durationMs() >= 0 && result.durationMs() < 60_000, String.valueOf(result.durationMs()));
    }

    @Test
    void given_aServerAnsweringWithoutAnIssuer_when_checkDiscovery_then_downNamingTheRealm() throws IOException {
        String base = serve(200, "{\"realm\":\"elicit\"}");

        CheckResult result = checks.check(target(base + "/realms/elicit", Kind.OIDC_DISCOVERY));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertEquals("HTTP 200 from " + base + "/realms/elicit/.well-known/openid-configuration is not an OIDC"
                + " discovery document; check the realm address", result.detail());
    }

    @Test
    void given_aRealmThatIsNotThere_when_checkDiscovery_then_downWithTheStatus() throws IOException {
        String base = serve(404, "{\"issuer\":\"x\"}");

        CheckResult result = checks.check(target(base + "/realms/nope", Kind.OIDC_DISCOVERY));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertTrue(result.detail().startsWith("HTTP 404 from "), result.detail());
    }

    // ---- HTTP -------------------------------------------------------------------------------------

    @Test
    void given_anyHttpAnswer_when_checkHttp_then_reachableWithTheStatus() throws IOException {
        String base = serve(500, null);

        CheckResult result = checks.check(target(base + "/report", Kind.HTTP));

        assertEquals(CheckResult.Status.UP, result.status(), "any HTTP answer counts as reachable");
        assertEquals("reachable, HTTP 500", result.detail());
        assertPlausible(result);
        assertEquals(List.of("GET /report"), requested, "BR-096: one read-only GET");
    }

    @Test
    void given_aRedirect_when_checkHttp_then_notFollowed() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 50);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestURI().toString());
            exchange.getResponseHeaders().add("Location", "/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        CheckResult result = checks.check(target("http://127.0.0.1:" + server.getAddress().getPort() + "/start", Kind.HTTP));

        assertEquals("reachable, HTTP 302", result.detail());
        assertEquals(List.of("/start"), requested);
    }

    @Test
    void given_aForbiddenAnswer_when_checkHttp_then_upWithTheLicenseHint() throws IOException {
        CheckResult result = checks.check(target(serve(403, null) + "/svg", Kind.HTTP));

        assertEquals(CheckResult.Status.UP, result.status());
        assertEquals("reachable, HTTP 403: license validation may have failed", result.detail());
    }

    @Test
    void given_anInterruptedThread_when_checkHttp_then_downAndTheInterruptKept() throws IOException {
        String address = serve(200, null);
        Thread.currentThread().interrupt();

        CheckResult result = checks.check(target(address, Kind.HTTP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertEquals("interrupted", result.detail());
        assertTrue(Thread.interrupted(), "the interrupt is restored");
    }

    @Test
    void given_anAddressThatIsNotAUri_when_checkHttp_then_downWithTheReason() {
        CheckResult result = checks.check(target("http://bad host/", Kind.HTTP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertTrue(result.detail().contains("Illegal character"), result.detail());
    }

    @Test
    void given_aClosedPort_when_checkHttp_then_down() throws IOException {
        CheckResult result = checks.check(target("http://127.0.0.1:" + closedPort() + "/", Kind.HTTP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertEquals("quarkus.oidc.auth-server-url", result.name());
        assertPlausible(result);
    }

    // ---- TCP --------------------------------------------------------------------------------------

    @Test
    void given_aListeningRelay_when_checkTcp_then_connected() throws IOException {
        try (ServerSocket relay = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = relay.getLocalPort();

            CheckResult bare = checks.check(target("127.0.0.1:" + port, Kind.TCP));
            CheckResult withScheme = checks.check(target("http://127.0.0.1:" + port, Kind.TCP));

            assertEquals(CheckResult.Status.UP, bare.status());
            assertEquals("connected to 127.0.0.1:" + port, bare.detail());
            assertPlausible(bare);
            assertEquals(CheckResult.Status.UP, withScheme.status());
            assertEquals(bare.detail(), withScheme.detail());
        }
    }

    @Test
    void given_aClosedPort_when_checkTcp_then_downWithTheReason() throws IOException {
        CheckResult result = checks.check(target("127.0.0.1:" + closedPort(), Kind.TCP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertTrue(result.detail().toLowerCase().contains("refused"), result.detail());
    }

    @Test
    void given_anAddressWithoutHostOrPort_when_checkTcp_then_unknown() {
        assertEquals(CheckResult.unknown("quarkus.oidc.auth-server-url", "address collector has no host and port"),
                checks.check(target("collector", Kind.TCP)));
        assertEquals(CheckResult.Status.UNKNOWN, checks.check(target("http://collector", Kind.TCP)).status());
        assertEquals(CheckResult.Status.UNKNOWN, checks.check(target("tcp://:4317", Kind.TCP)).status());
    }

    @Test
    void given_portZero_when_checkTcp_then_triedRatherThanCalledUnaddressed() {
        assertEquals(CheckResult.Status.DOWN, checks.check(target("127.0.0.1:0", Kind.TCP)).status());
    }

    // ---- failure reasons --------------------------------------------------------------------------

    private static String reason(Exception e) throws Exception {
        Method reason = ConnectionChecks.class.getDeclaredMethod("reason", Exception.class);
        reason.setAccessible(true);
        return (String) reason.invoke(null, e);
    }

    @Test
    void given_aSocketTimeout_when_reason_then_theBoundIsNamed() throws Exception {
        assertEquals("timed out after 5 s", reason(new SocketTimeoutException("Read timed out")));
    }

    @Test
    void given_aFailureWithoutAMessage_when_reason_then_itsTypeIsNamed() throws Exception {
        assertEquals("IOException", reason(new IOException()));
        assertEquals("IOException", reason(new IOException("  ")));
        assertEquals("no route", reason(new IOException("no route")));
    }

    // ---- results ----------------------------------------------------------------------------------

    @Test
    void given_eachOutcome_when_built_then_itsStatusAndWhetherItIsUp() {
        assertEquals(new CheckResult("n", CheckResult.Status.UP, "d", 3), CheckResult.up("n", "d", 3));
        assertEquals(new CheckResult("n", CheckResult.Status.DOWN, "d", 3), CheckResult.down("n", "d", 3));
        assertEquals(new CheckResult("n", CheckResult.Status.UNKNOWN, "d", 0), CheckResult.unknown("n", "d"));
        assertTrue(CheckResult.up("n", "d", 0).isUp());
        assertTrue(!CheckResult.down("n", "d", 0).isUp() && !CheckResult.unknown("n", "d").isUp());
    }
}
