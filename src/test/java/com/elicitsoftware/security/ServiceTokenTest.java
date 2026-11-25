package com.elicitsoftware.security;

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

import com.elicitsoftware.security.ServiceToken.Origin;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-018: Apply Survey Definition — the service token the console's calls carry (NFR-024):
 * which addresses receive it (BR-159, BR-160), what happens without one (BR-161) and what is
 * checked at start (BR-162). Plain objects, no network.
 */
class ServiceTokenTest {

    private static final String TOKEN = "unit-test-service-token-0123456789abcdef";
    private static final String BEARER = "Bearer " + TOKEN;

    private static ServiceToken token(String value, String trustedOrigins, String surveyUrl) {
        return new ServiceToken(Optional.ofNullable(value), Optional.ofNullable(trustedOrigins), surveyUrl);
    }

    private static ServiceToken configured() {
        return token(TOKEN, "http://fhhs:8080, https://Reports.Example.org", "http://survey:8080/elicit");
    }

    private static Optional<String> headerFor(String address) {
        return configured().authorizationFor(URI.create(address));
    }

    /** Collects every WARN and above the class logs while the action runs. */
    private static List<String> captureLog(Runnable action) {
        List<String> lines = new ArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ServiceToken.class.getName());
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                lines.add(record.getMessage() + " " + java.util.Arrays.toString(record.getParameters()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return lines;
    }

    // --- BR-160: which origins receive the token ---------------------------------------------

    @Test
    void given_anAddressUnderTheSurveyAddress_when_asked_then_itReceivesTheToken() {
        assertEquals(Optional.of(BEARER), headerFor("http://survey:8080/api/reports/respondent-summary"));
        assertEquals(Optional.of(BEARER), headerFor("http://survey:8080/elicit/api/etl/build?survey=x"));
        assertEquals(Optional.of(BEARER), headerFor("HTTP://SURVEY:8080/"), "scheme and host compare in lower case");
    }

    @Test
    void given_anAddressAtAListedOrigin_when_asked_then_itReceivesTheToken() {
        assertEquals(Optional.of(BEARER), headerFor("http://fhhs:8080/proband/report"));
        assertEquals(Optional.of(BEARER), headerFor("https://reports.example.org/x"), "a listed host compares in lower case");
        assertEquals(Optional.of(BEARER), headerFor("https://reports.example.org:443/x"), "443 is https's port when none is written");
    }

    @Test
    void given_anotherPortSchemeOrHost_when_asked_then_itReceivesNothing() {
        assertEquals(Optional.empty(), headerFor("http://fhhs:8081/proband/report"));
        assertEquals(Optional.empty(), headerFor("http://fhhs/proband/report"), "no port is 80, not 8080");
        assertEquals(Optional.empty(), headerFor("https://fhhs:8080/proband/report"));
        assertEquals(Optional.empty(), headerFor("http://reports.example.org/x"));
        assertEquals(Optional.empty(), headerFor("https://reports.example.org:8443/x"));
        assertEquals(Optional.empty(), headerFor("http://other:8080/proband/report"));
    }

    @Test
    void given_anAddressThatOnlyLooksTrusted_when_asked_then_itReceivesNothing() {
        // the trusted name as user information of another host
        assertEquals(Optional.empty(), headerFor("http://fhhs:8080@evil/"));
        assertEquals(Optional.empty(), headerFor("http://survey:8080@evil.test/api/etl/build"));
        // the trusted name as the start of another host
        assertEquals(Optional.empty(), headerFor("http://fhhs:8080.evil/"));
        assertEquals(Optional.empty(), headerFor("http://fhhs.evil:8080/"));
        assertEquals(Optional.empty(), headerFor("http://survey.evil.test:8080/"));
        // the trusted name as the end of another host
        assertEquals(Optional.empty(), headerFor("http://evilfhhs:8080/"));
        assertEquals(Optional.empty(), headerFor("http://evil.fhhs:8080/"));
        // the trusted address elsewhere in the address
        assertEquals(Optional.empty(), headerFor("http://evil.test/?u=http://fhhs:8080/"));
        assertEquals(Optional.empty(), headerFor("http://evil.test/http://fhhs:8080/"));
        assertEquals(Optional.empty(), headerFor("http://evil.test/#http://fhhs:8080"));
        // user information beside the right host
        assertEquals(Optional.empty(), headerFor("http://user@fhhs:8080/"));
        assertEquals(Optional.empty(), headerFor("http://user:pw@fhhs:8080/"));
        assertEquals(Optional.empty(), headerFor("http://@fhhs:8080/"));
    }

    @Test
    void given_anAddressWithoutAnHttpOrigin_when_asked_then_itReceivesNothing() {
        assertEquals(Optional.empty(), headerFor("ftp://fhhs:8080/"));
        assertEquals(Optional.empty(), headerFor("file:///etc/hosts"));
        assertEquals(Optional.empty(), headerFor("/api/reports/respondent-summary"), "an unresolved path has no origin");
        assertEquals(Optional.empty(), headerFor("//fhhs:8080/x"), "no scheme");
        assertEquals(Optional.empty(), headerFor("http:///x"), "no host");
        assertEquals(Optional.empty(), headerFor("mailto:someone@fhhs"));
        assertEquals(Optional.empty(), configured().authorizationFor(null));
        assertFalse(configured().isTrusted(null));
    }

    @Test
    void given_noTrustedOrigins_when_asked_then_onlyTheSurveyOriginIsTrusted() {
        ServiceToken token = token(TOKEN, null, "https://elicit.example.org/survey");
        assertEquals(Set.of(new Origin("https", "elicit.example.org", 443)), token.trustedOrigins());
        assertEquals(Optional.of(BEARER), token.authorizationFor(URI.create("https://elicit.example.org/survey/api/x")));
        assertEquals(Optional.empty(), token.authorizationFor(URI.create("http://fhhs:8080/proband/report")));
    }

    @Test
    void given_aSurveyAddressWithoutAnOrigin_when_asked_then_nothingIsTrustedByIt() {
        assertEquals(Set.of(), token(TOKEN, " ", "not an address").trustedOrigins());
        assertEquals(Set.of(), token(TOKEN, "", " ").trustedOrigins());
        assertEquals(Set.of(), token(TOKEN, "", null).trustedOrigins());
        assertEquals(Set.of(), token(TOKEN, "", "http://user@survey:8080").trustedOrigins());
    }

    @Test
    void given_origins_when_reduced_then_theyCarryLowerCaseSchemeHostAndTheEffectivePort() {
        assertEquals(Optional.of(new Origin("http", "fhhs", 80)), Origin.of(URI.create("HTTP://FHHS/a?b#c")));
        assertEquals(Optional.of(new Origin("https", "fhhs", 443)), Origin.of(URI.create("https://fhhs")));
        assertEquals(Optional.of(new Origin("http", "127.0.0.1", 8082)), Origin.of(URI.create("http://127.0.0.1:8082/")));
        assertEquals("http://fhhs:80", new Origin("http", "fhhs", 80).toString());
    }

    // --- BR-160: the entries are checked at start ---------------------------------------------

    @Test
    void given_wellFormedEntries_when_parsed_then_blanksAreSkipped() {
        assertEquals(Set.of(new Origin("http", "host.docker.internal", 8082), new Origin("https", "a.example", 443)),
                ServiceToken.parseTrustedOrigins(" http://host.docker.internal:8082 ,, https://A.example ,"));
        assertEquals(Set.of(), ServiceToken.parseTrustedOrigins(null));
        assertEquals(Set.of(), ServiceToken.parseTrustedOrigins("  "));
    }

    @Test
    void given_anEntryThatIsNotAnOrigin_when_started_then_theStartFailsNamingTheVariableAndPosition() {
        String[] malformed = {
                "http://fhhs:8080/reports", "http://fhhs:8080/", "http://fhhs:8080?x=1", "http://fhhs:8080#f",
                "http://user:secretpw@fhhs:8080", "fhhs:8080", "fhhs", "ftp://fhhs", "http://", "http://fh hs",
                "http://fhhs:8080.evil"};
        for (String entry : malformed) {
            ServiceToken token = token(TOKEN, "http://ok:1, " + entry, "http://survey:8080");
            IllegalStateException failure = assertThrows(IllegalStateException.class, token::validate, entry);
            assertTrue(failure.getMessage().startsWith("ELICIT_SERVICE_TRUSTED_ORIGINS: entry 2 is not an origin"),
                    failure.getMessage());
            assertFalse(failure.getMessage().contains("secretpw"), "the entry is not repeated");
            assertFalse(failure.getMessage().contains(TOKEN));
        }
    }

    // --- BR-161: without a token ----------------------------------------------------------------

    @Test
    void given_noToken_when_aTrustedAddressIsAsked_then_theCallIsRefusedNotSentBare() {
        for (String unset : new String[]{null, "", "   "}) {
            ServiceToken token = token(unset, "http://fhhs:8080", "http://survey:8080");
            assertFalse(token.isConfigured());
            ServiceToken.NotConfiguredException refused = assertThrows(ServiceToken.NotConfiguredException.class,
                    () -> token.authorizationFor(URI.create("http://fhhs:8080/proband/report")));
            assertEquals("service token not configured", refused.getMessage());
            assertThrows(ServiceToken.NotConfiguredException.class, token::authorization);
            assertEquals(Optional.empty(), token.authorizationFor(URI.create("http://elsewhere/report")),
                    "an address that would not have received the token is called as before");
            assertEquals("ServiceToken[not set]", token.toString());
        }
    }

    // --- BR-162: checked at start -------------------------------------------------------------

    @Test
    void given_aShortToken_when_started_then_theStartFailsWithoutTheValueOrItsLength() {
        String shortToken = "Sh0rt-but-not-empty-31-chars-xx";
        assertEquals(31, shortToken.length());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> token(shortToken, null, "http://survey:8080").validate());
        assertEquals("ELICIT_SERVICE_TOKEN is too short: a service token must be at least 32 characters."
                + " Generate one with: openssl rand -hex 32", failure.getMessage());
        assertFalse(failure.getMessage().contains(shortToken));
        assertFalse(failure.getMessage().contains("31"));
    }

    @Test
    void given_aTokenOfTheMinimumLength_when_started_then_itStartsQuietly() {
        String exact = "x".repeat(ServiceToken.MINIMUM_LENGTH);
        List<String> log = captureLog(() -> assertDoesNotThrow(() -> token(exact, null, "http://survey:8080").validate()));
        assertEquals(List.of(), log);
        assertDoesNotThrow(() -> token("  " + exact + "  ", null, "http://survey:8080").validate(),
                "surrounding blanks are not part of the token");
    }

    @Test
    void given_noToken_when_started_then_itStartsWithOneWarningNamingTheVariable() {
        List<String> log = captureLog(() -> assertDoesNotThrow(() -> token(" ", null, "http://survey:8080").validate()));
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.get(0).startsWith("ELICIT_SERVICE_TOKEN is not set."), log.get(0));
    }

    @Test
    void given_theDevelopmentValue_when_started_then_itIsWarnedAboutWithoutBeingWritten() {
        ServiceToken token = token(ServiceToken.DEVELOPMENT_VALUE, null, "http://survey:8080");
        assertTrue(token.isDevelopmentValue());
        List<String> log = captureLog(() -> assertDoesNotThrow(token::validate));
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.get(0).startsWith("ELICIT_SERVICE_TOKEN is the well-known development value."), log.get(0));
        assertFalse(log.get(0).contains(ServiceToken.DEVELOPMENT_VALUE));
        assertFalse(configured().isDevelopmentValue());
        assertFalse(token(null, null, "http://survey:8080").isDevelopmentValue());
    }

    // --- NFR-024: the value stays inside ------------------------------------------------------

    @Test
    void given_aConfiguredToken_when_described_then_onlyItsPresenceShows() {
        ServiceToken token = configured();
        assertTrue(token.isConfigured());
        assertEquals("ServiceToken[set]", token.toString());
        assertEquals(BEARER, token.authorization());
        assertFalse(token.trustedOrigins().toString().contains(TOKEN));
    }
}
