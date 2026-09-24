package com.elicitsoftware.service;

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

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Asks the Survey application to rebuild its reporting star schema after a survey definition
 * has been installed or updated here (UC-014, UC-017, UC-018).
 * <p>
 * Survey builds {@code surveyreport} -- dimension rows and tables, fact columns, views -- from
 * the survey definitions present when it starts, and nothing else rebuilds it. Without this
 * call a survey applied through Admin has no dimensions until Survey is restarted. Survey's
 * {@code POST /api/etl/build} (its UC-008) runs that build again on request, idempotently.
 * <p>
 * The outcome never fails the apply: the definition is already committed by the time this
 * runs, and a reporting schema that is behind is recoverable (repeat the call, or restart
 * Survey) where a rolled-back apply is not. A failure is returned as a line for the result
 * dialog and logged at WARN.
 * <p>
 * Configuration: {@code elicit.survey.url} (env {@code ELICIT_SURVEY_URL}, default
 * {@code http://survey:8080}, the compose service name) and
 * {@code elicit.survey.etl-build.enabled} (default {@code true}) to skip the call entirely.
 */
@ApplicationScoped
public class ReportingSchemaRebuildClient {

    /** Survey's endpoint, relative to {@code elicit.survey.url}. */
    public static final String PATH = "/api/etl/build";

    /**
     * The bound on the whole call. The build is synchronous on Survey's side and back-fills
     * fact rows for every finalized respondent still missing them, so it is allowed far longer
     * than a diagnostic probe; a build that outlives this still completes on Survey.
     */
    public static final Duration TIMEOUT = Duration.ofSeconds(60);

    /** Connecting is the one part that should be quick: Survey is a sibling container. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** What the call achieved. */
    public enum Status {
        /** Survey answered 200: the schema is current. */
        REBUILT,
        /** Survey answered anything else, or could not be reached. */
        FAILED,
        /** {@code elicit.survey.etl-build.enabled=false}: no call was made. */
        SKIPPED
    }

    /**
     * The outcome, with the line the apply result shows for it.
     *
     * @param status  rebuilt, failed or skipped
     * @param message Survey's summary or the failure reason; empty when skipped
     */
    public record Outcome(Status status, String message) {

        /**
         * The line appended to an apply result: "Reporting schema rebuilt." or "Reporting
         * schema not rebuilt: reason", or {@code null} when the call was skipped so a
         * deployment that turned it off sees nothing about it.
         */
        public String summaryLine() {
            return switch (status) {
                case REBUILT -> "Reporting schema rebuilt.";
                case FAILED -> "Reporting schema not rebuilt: " + message;
                case SKIPPED -> null;
            };
        }
    }

    @ConfigProperty(name = "elicit.survey.url", defaultValue = "http://survey:8080")
    String surveyUrl;

    @ConfigProperty(name = "elicit.survey.etl-build.enabled", defaultValue = "true")
    boolean enabled;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Default constructor for CDI.
     */
    public ReportingSchemaRebuildClient() {
        // CDI managed bean
    }

    /** The address the call goes to, for diagnostics. */
    public String endpoint() {
        String base = surveyUrl == null ? "" : surveyUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + PATH;
    }

    /**
     * Calls Survey's rebuild endpoint and reports what happened. Never throws.
     *
     * @return the outcome; {@link Status#SKIPPED} when the call is disabled by configuration
     */
    public Outcome rebuild() {
        if (!enabled) {
            Log.debug("Reporting schema rebuild skipped: elicit.survey.etl-build.enabled=false");
            return new Outcome(Status.SKIPPED, "");
        }
        String endpoint = endpoint();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("Accept", "application/json")
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String message = messageOf(response.body(), "HTTP " + response.statusCode());
            if (response.statusCode() == 200) {
                Log.infof("Reporting schema rebuilt by %s: %s", endpoint, message);
                return new Outcome(Status.REBUILT, message);
            }
            Log.warnf("Reporting schema not rebuilt: %s answered HTTP %d: %s", endpoint,
                    response.statusCode(), message);
            return new Outcome(Status.FAILED, message);
        } catch (HttpTimeoutException e) {
            String reason = "no answer from " + endpoint + " within " + TIMEOUT.toSeconds() + " s";
            Log.warn("Reporting schema not rebuilt: " + reason);
            return new Outcome(Status.FAILED, reason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("Reporting schema not rebuilt: interrupted while waiting for " + endpoint);
            return new Outcome(Status.FAILED, "interrupted while waiting for " + endpoint);
        } catch (Exception e) {
            String reason = rootMessage(e) + " (" + endpoint + ")";
            Log.warn("Reporting schema not rebuilt: " + reason, e);
            return new Outcome(Status.FAILED, reason);
        }
    }

    /**
     * Survey answers {@code {"status": ..., "message": ...}}; the message is the part worth
     * showing. Anything that is not that shape (a proxy's error page, say) falls back.
     */
    static String messageOf(String body, String fallback) {
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonObject object = reader.readObject();
            String message = object.getString("message", null);
            return message == null || message.isBlank() ? fallback : message.trim();
        } catch (RuntimeException e) {
            String trimmed = body.trim();
            return fallback + ": " + (trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
