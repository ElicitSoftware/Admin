package com.elicitsoftware.test;

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

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A stand-in for the Survey application's {@code POST /api/etl/build} (Survey UC-008), on the
 * fixed port {@code %test.elicit.survey.url} names, so the tests for the reporting schema
 * rebuild call (UC-014/UC-017/UC-018) can script Survey's answer without a Survey container.
 * <p>
 * A plain JDK {@link HttpServer}: the project has no WireMock dependency and the contract is
 * one endpoint with a two-field body. Start it in {@code @BeforeAll}, script each test's
 * answer with {@link #respond(int, String)} or {@link #hang(long)}, and stop it in
 * {@code @AfterAll}; test classes run one at a time, so the port is never shared.
 */
public final class SurveyEtlStub {

    /** Must match {@code %test.elicit.survey.url} in src/test/resources/application.properties. */
    public static final int PORT = 18090;

    private final HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{\"status\":\"ok\",\"message\":\"stubbed\"}";
    private volatile long delayMillis = 0;
    private final List<String> requests = new ArrayList<>();

    private SurveyEtlStub(HttpServer server) {
        this.server = server;
    }

    /** Starts listening on {@link #PORT}. */
    public static SurveyEtlStub start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        SurveyEtlStub stub = new SurveyEtlStub(server);
        server.createContext("/", exchange -> {
            synchronized (stub.requests) {
                stub.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            }
            if (stub.delayMillis > 0) {
                try {
                    Thread.sleep(stub.delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = stub.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return stub;
    }

    /** Scripts the next answers: this status and body, sent at once. */
    public SurveyEtlStub respond(int status, String body) {
        this.status = status;
        this.body = body;
        this.delayMillis = 0;
        return this;
    }

    /** Scripts a slow Survey: every answer is held for this long before the scripted body. */
    public SurveyEtlStub hang(long millis) {
        this.delayMillis = millis;
        return this;
    }

    /** Every request received so far, as "METHOD /path". */
    public List<String> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    /** Forgets the requests received so far. */
    public void reset() {
        synchronized (requests) {
            requests.clear();
        }
        respond(200, "{\"status\":\"ok\",\"message\":\"stubbed\"}");
    }

    /** Stops listening; in-flight exchanges get up to a second. */
    public void stop() {
        server.stop(1);
        try {
            TimeUnit.MILLISECONDS.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
