package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Admin
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Asks the Survey application to rename a survey's reporting schema (UC-030; Survey UC-010).
 * <p>
 * The console never touches a reporting schema itself (BR-117): it connects as
 * {@code surveyadmin_user}, which owns none of them, and the name lives on the survey row that
 * Survey's build maintains. Survey's {@code POST /api/etl/schema/<key>/rename?name=<new>}
 * validates the name, renames the schema and records the new name in one transaction, so a
 * failure leaves the old name everywhere.
 * <p>
 * Same base address ({@code elicit.survey.url}) and bounds as {@link ReportingSchemaRebuildClient}.
 */
@ApplicationScoped
public class ReportingSchemaRenameClient {

    /** BR-116: what a schema name must look like, the same rule Survey applies. */
    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    /** What the call achieved. */
    public enum Status {
        /** Survey answered 200: the schema carries the new name. */
        RENAMED,
        /** Survey answered 400: the name is not acceptable. */
        INVALID,
        /** Survey answered 409 {@code taken}: another survey, or a schema nobody owns, has the name. */
        TAKEN,
        /** Survey answered 409 {@code unbuilt}: the survey has no schema yet. */
        UNBUILT,
        /** Survey answered 404: no survey has that key. */
        UNKNOWN,
        /** Survey answered 500, or could not be reached. */
        FAILED
    }

    /**
     * The outcome.
     *
     * @param status  what happened
     * @param message Survey's message, or the failure reason, already in the administrator's language where this console words it
     * @param schema  the schema's name afterwards: the new one on success, otherwise the old one, or null when Survey gave none
     */
    public record Outcome(Status status, String message, String schema) {
    }

    @ConfigProperty(name = "elicit.survey.url", defaultValue = "http://survey:8080")
    String surveyUrl;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(ReportingSchemaRebuildClient.CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Default constructor for CDI.
     */
    public ReportingSchemaRenameClient() {
        // CDI managed bean
    }

    /**
     * Why a name is not acceptable (BR-116), in the administrator's language, or null when it is.
     * The console applies this before asking so ordinary mistakes are caught in the field; Survey
     * remains the authority.
     *
     * @param name the candidate
     * @return the objection or null
     */
    public static String objection(String name) {
        if (name == null || name.isBlank()) {
            return Translations.get("reportingRename.nameRequired");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            return Translations.get("reportingRename.namePattern");
        }
        if (name.equals("survey") || name.equals("surveyreport") || name.equals("public")
                || name.equals("information_schema") || name.startsWith("pg_")) {
            return Translations.get("reportingRename.nameReserved", name);
        }
        return null;
    }

    /** The address a rename of one survey's schema goes to. */
    public String endpoint(UUID surveyKey, String newName) {
        return ReportingSchemaRebuildClient.baseUrl(surveyUrl) + "/api/etl/schema/" + surveyKey + "/rename?name="
                + URLEncoder.encode(newName, StandardCharsets.UTF_8);
    }

    /**
     * Asks Survey to rename the survey's reporting schema and reports what happened. Never throws.
     *
     * @param surveyKey the survey's portable key
     * @param newName   the name the schema should carry
     * @return the outcome
     */
    public Outcome rename(UUID surveyKey, String newName) {
        String endpoint = endpoint(surveyKey, newName);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("Accept", "application/json")
                    .timeout(ReportingSchemaRebuildClient.TIMEOUT)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return outcomeOf(response.statusCode(), response.body(), endpoint);
        } catch (HttpTimeoutException e) {
            Log.warn("Reporting schema not renamed: no answer from " + endpoint + " within "
                    + ReportingSchemaRebuildClient.TIMEOUT.toSeconds() + " s");
            return new Outcome(Status.FAILED, Translations.get("reportingRebuild.noAnswer",
                    String.valueOf(endpoint), String.valueOf(ReportingSchemaRebuildClient.TIMEOUT.toSeconds())), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("Reporting schema not renamed: interrupted while waiting for " + endpoint);
            return new Outcome(Status.FAILED, Translations.get("reportingRebuild.interrupted", String.valueOf(endpoint)), null);
        } catch (Exception e) {
            String reason = ReportingSchemaRebuildClient.rootMessage(e) + " (" + endpoint + ")";
            Log.warn("Reporting schema not renamed: " + reason, e);
            return new Outcome(Status.FAILED, reason, null);
        }
    }

    /**
     * Maps Survey's answer, {@code {"status": ..., "message": ..., "schema": ...}}, to an outcome.
     * Package-private so the mapping is testable without a server.
     */
    static Outcome outcomeOf(int httpStatus, String body, String endpoint) {
        String status = null;
        String message = null;
        String schema = null;
        if (body != null && !body.isBlank()) {
            try (JsonReader reader = Json.createReader(new StringReader(body))) {
                JsonObject object = reader.readObject();
                status = object.getString("status", null);
                message = object.getString("message", null);
                schema = object.isNull("schema") ? null : object.getString("schema", null);
            } catch (RuntimeException e) {
                // not Survey's shape: a proxy's error page, say
            }
        }
        if (message == null || message.isBlank()) {
            message = "HTTP " + httpStatus;
        }
        Status outcome = switch (httpStatus) {
            case 200 -> Status.RENAMED;
            case 400 -> Status.INVALID;
            case 404 -> Status.UNKNOWN;
            case 409 -> "unbuilt".equals(status) ? Status.UNBUILT : Status.TAKEN;
            default -> Status.FAILED;
        };
        if (outcome == Status.RENAMED) {
            Log.infof("Reporting schema renamed by %s: %s", endpoint, message);
        } else {
            Log.warnf("Reporting schema not renamed: %s answered HTTP %d: %s", endpoint, httpStatus, message);
        }
        return new Outcome(outcome, message, schema);
    }
}
