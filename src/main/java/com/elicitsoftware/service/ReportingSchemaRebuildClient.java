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

import com.elicitsoftware.admin.i18n.Translations;
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
import java.util.UUID;

/**
 * Asks the Survey application to rebuild one survey's reporting star schema after that survey's
 * definition has been installed or updated here (UC-014, UC-017, UC-018).
 * <p>
 * Every survey has a reporting schema of its own (Survey UC-008 BR-006), which Survey builds --
 * schema, dimension rows and tables, fact columns, views -- from the definition present when it
 * starts, and nothing else rebuilds it. Without this call a survey applied through Admin has no
 * schema until Survey is restarted. Survey's {@code POST /api/etl/build?survey=<key>} (its
 * UC-008) runs that build again on request, idempotently, for the one survey named; the request
 * names the survey so no other survey's schema is touched.
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
                case REBUILT -> Translations.get("reportingRebuild.rebuilt");
                case FAILED -> Translations.get("reportingRebuild.notRebuilt", message);
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
        return baseUrl(surveyUrl) + PATH;
    }

    /** {@code elicit.survey.url} without a trailing slash, the base every Survey endpoint hangs off. */
    static String baseUrl(String surveyUrl) {
        String base = surveyUrl == null ? "" : surveyUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * The address a rebuild of one survey goes to: the endpoint with the survey named.
     *
     * @param surveyKey the survey's portable key, or null to ask Survey to build every survey
     */
    public String endpoint(UUID surveyKey) {
        return surveyKey == null ? endpoint() : endpoint() + "?survey=" + surveyKey;
    }

    /**
     * Calls Survey's rebuild endpoint for one survey and reports what happened. Never throws.
     *
     * @param surveyKey the portable key of the survey just applied; null asks Survey to build
     *                  every survey, which only an apply that could not learn the key does
     * @return the outcome; {@link Status#SKIPPED} when the call is disabled by configuration
     */
    public Outcome rebuild(UUID surveyKey) {
        if (!enabled) {
            Log.debug("Reporting schema rebuild skipped: elicit.survey.etl-build.enabled=false");
            return new Outcome(Status.SKIPPED, "");
        }
        String endpoint = endpoint(surveyKey);
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
            // Survey answers 409 for exactly one reason, a disabled ETL, and words it in English
            // because a REST call has no reader's language; say it in the administrator's here.
            return new Outcome(Status.FAILED, response.statusCode() == 409
                    ? Translations.get("reportingRebuild.disabled") : message);
        } catch (HttpTimeoutException e) {
            // The log stays in English; the administrator reads the reason in their own language.
            Log.warn("Reporting schema not rebuilt: no answer from " + endpoint + " within " + TIMEOUT.toSeconds() + " s");
            return new Outcome(Status.FAILED, Translations.get("reportingRebuild.noAnswer",
                    String.valueOf(endpoint), String.valueOf(TIMEOUT.toSeconds())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("Reporting schema not rebuilt: interrupted while waiting for " + endpoint);
            return new Outcome(Status.FAILED, Translations.get("reportingRebuild.interrupted", String.valueOf(endpoint)));
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

    static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
