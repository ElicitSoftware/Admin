package com.elicitsoftware.rest;

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

import com.elicitsoftware.admin.upload.MultipartBody;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.service.CsvImportService;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-015: Generate Survey Access Code and UC-009: View Security Diagnostics — the parts of
 * {@link AccessCodeService} that need no database: the CSV upload hand-off, the per-request
 * isolation of a batch registration, and the role/JWT diagnostic text.
 */
class AccessCodeServiceUnitTest {

    /** A token whose claims are the map; a claim named in {@code broken} throws when read. */
    private static JsonWebToken token(String name, Map<String, Object> claims, Set<String> broken) {
        return new JsonWebToken() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Set<String> getClaimNames() {
                return claims == null ? null : new LinkedHashSet<>(claims.keySet());
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getClaim(String claimName) {
                if (broken.contains(claimName)) {
                    throw new IllegalStateException("cannot read " + claimName);
                }
                return claims == null ? null : (T) claims.get(claimName);
            }
        };
    }

    private static SecurityIdentity alice() {
        return QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("alice")).addRole("elicit_admin").build();
    }

    private static AccessCodeService service(SecurityIdentity identity, JsonWebToken jwt) {
        AccessCodeService service = new AccessCodeService();
        service.securityIdentity = identity;
        service.jwt = jwt;
        return service;
    }

    @Test
    void given_theProbeEndpoint_when_called_then_itAnswers() {
        assertEquals("access code service test", new AccessCodeService().test());
    }

    @Test
    void given_noUploadedFile_when_aCsvIsImported_then_theResponseCarriesTheError() {
        AddResponse response = new AccessCodeService().importCsv(new MultipartBody());
        assertEquals(List.of("No CSV file provided in the 'file' field"), response.getErrors());
    }

    @Test
    void given_anUploadedFile_when_aCsvIsImported_then_theCsvServiceAnswers() {
        AddResponse expected = new AddResponse();
        InputStream file = new ByteArrayInputStream(new byte[0]);
        InputStream[] received = new InputStream[1];
        AccessCodeService service = new AccessCodeService();
        service.csvImportService = new CsvImportService(null) {
            @Override
            public AddResponse importSubjects(InputStream csvInputStream) {
                received[0] = csvInputStream;
                return expected;
            }
        };
        MultipartBody body = new MultipartBody();
        body.file = file;

        assertSame(expected, service.importCsv(body));
        assertSame(file, received[0]);
    }

    @Test
    void given_aCsvImportThatFails_when_imported_then_theFailureMessageIsTheError() {
        AccessCodeService service = new AccessCodeService();
        service.csvImportService = new CsvImportService(null) {
            @Override
            public AddResponse importSubjects(InputStream csvInputStream) throws Exception {
                throw new Exception("Import completed with errors:\nLine 1: Email is required");
            }
        };
        MultipartBody body = new MultipartBody();
        body.file = new ByteArrayInputStream(new byte[0]);
        assertEquals(List.of("Import completed with errors:\nLine 1: Email is required"), service.importCsv(body).getErrors());
    }

    @Test
    void given_aBatchWhoseTransactionsCannotRun_when_registered_then_eachRequestReportsItsOwnFailure() {
        AddRequest first = new AddRequest();
        first.xid = "X1";
        AddRequest second = new AddRequest();
        second.xid = "X2";

        AddResponse response = new AccessCodeService().putSubjects(List.of(first, second));

        assertEquals(2, response.getSubjects().size());
        assertTrue(response.getSubjects().get(0).getImportStatus().startsWith("Unexpected error processing X1: "),
                response.getSubjects().get(0).getImportStatus());
        assertTrue(response.getSubjects().get(1).getImportStatus().startsWith("Unexpected error processing X2: "));
        assertTrue(response.getErrors().isEmpty());
    }

    @Test
    void given_anIdentityWithoutPrincipal_when_rolesAreShown_then_onlyThatIsSaid() {
        SecurityIdentity none = (SecurityIdentity) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{SecurityIdentity.class}, (p, m, a) -> null);
        assertEquals("No principal found\n", service(none, null).roles());
    }

    @Test
    void given_aParsedToken_when_rolesAreShown_then_theIdentityAndEveryClaimAreListed() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "s-1");
        claims.put("iss", "https://kc/realms/elicit");
        claims.put("groups", List.of("a"));
        String text = service(alice(), token("alice", claims, Set.of())).roles();

        assertEquals("=== Security Identity ===\n"
                + "Principal Name: alice\n"
                + "Is Anonymous: false\n"
                + "Roles from SecurityIdentity: [elicit_admin]\n\n"
                + "=== JWT Token Information ===\n"
                + "Token Name: alice\n"
                + "Subject (sub): s-1\n"
                + "Issuer (iss): https://kc/realms/elicit\n"
                + "=== Attempting to read claims ===\n"
                + "Found 3 claims:\n"
                + "  sub: s-1\n"
                + "  iss: https://kc/realms/elicit\n"
                + "  groups: [a]\n", text);
    }

    @Test
    void given_anUnparsedToken_when_rolesAreShown_then_theLikelyCausesAreListed() {
        String text = service(alice(), token(null, Map.of(), Set.of())).roles();
        assertTrue(text.contains("Token Name: null (JWT not parsed)\n"), text);
        assertTrue(text.contains("Subject (sub): null\n"));
        assertTrue(text.contains("Issuer (iss): null\n"));
        assertTrue(text.contains("JWT object exists but has no claims - token validation likely failed\n"));
        assertTrue(text.contains("  6. JWKS endpoint unreachable for signature verification\n\n"));
        assertTrue(text.endsWith("getClaimNames() returned null or empty - JWT was not successfully parsed\n"), text);

        String noNames = service(alice(), token("n", null, Set.of())).roles();
        assertFalse(noNames.contains("has no claims"), "a token with a name is parsed");
        assertTrue(noNames.endsWith("getClaimNames() returned null or empty - JWT was not successfully parsed\n"));
    }

    @Test
    void given_aClaimThatCannotBeRead_when_rolesAreShown_then_theErrorIsShownInItsPlace() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "s-1");
        claims.put("iss", "i");
        claims.put("bad", 1);
        String text = service(alice(), token("alice", claims, Set.of("bad"))).roles();
        assertTrue(text.contains("  bad: Error reading - cannot read bad\n"), text);
        assertTrue(text.contains("  sub: s-1\n"));
    }

    @Test
    void given_unreadableSubjectAndIssuer_when_rolesAreShown_then_eachErrorIsShown() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("x", 1);
        JsonWebToken jwt = token(null, claims, Set.of("sub", "iss"));
        String text = service(alice(), jwt).roles();
        assertTrue(text.contains("Subject (sub): Error - cannot read sub\n"), text);
        assertTrue(text.contains("Issuer (iss): Error - cannot read iss\n"), text);
        assertTrue(text.contains("=== JWT Error ===\n"), "with no name, the no-claims check reads sub again and fails");
        assertTrue(text.contains("Unexpected error accessing JWT: java.lang.IllegalStateException: cannot read sub\n"), text);
    }

    @Test
    void given_aTokenWhoseNameAndClaimNamesFail_when_rolesAreShown_then_theFailuresAreReported() {
        JsonWebToken jwt = new JsonWebToken() {
            private int nameCalls;

            @Override
            public String getName() {
                if (nameCalls++ == 0) {
                    throw new IllegalStateException("no name");
                }
                return "alice";
            }

            @Override
            public Set<String> getClaimNames() {
                throw new IllegalStateException("no names", new RuntimeException("root"));
            }

            @Override
            public <T> T getClaim(String claimName) {
                return null;
            }
        };
        String text = service(alice(), jwt).roles();
        assertTrue(text.contains("Token Name: Error - no name\n"), text);
        assertTrue(text.endsWith("Error iterating claims: no names\n"), text);
    }

    @Test
    void given_aFailureOutsideTheGuardedReads_when_rolesAreShown_then_itsCauseIsNamed() {
        JsonWebToken jwt = new JsonWebToken() {
            private int nameCalls;

            @Override
            public String getName() {
                if (nameCalls++ > 0) {
                    throw new IllegalStateException("second read", new IllegalArgumentException("why"));
                }
                return "alice";
            }

            @Override
            public Set<String> getClaimNames() {
                return Set.of();
            }

            @Override
            public <T> T getClaim(String claimName) {
                return null;
            }
        };
        String text = service(alice(), jwt).roles();
        assertTrue(text.endsWith("\n=== JWT Error ===\n"
                + "Unexpected error accessing JWT: java.lang.IllegalStateException: second read\n"
                + "Caused by: java.lang.IllegalArgumentException: why\n"), text);
    }

    @Test
    void given_noToken_when_rolesAreShown_then_theMissingBearerTokenIsExplained() {
        String text = service(alice(), null).roles();
        assertTrue(text.endsWith("\n=== JWT Token ===\n"
                + "JWT is null - token may not be properly injected\n"
                + "This usually means no Bearer token was provided in the Authorization header\n"), text);
        assertFalse(text.contains("=== JWT Error ==="));
    }
}
