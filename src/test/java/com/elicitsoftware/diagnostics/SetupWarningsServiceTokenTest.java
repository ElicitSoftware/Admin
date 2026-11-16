package com.elicitsoftware.diagnostics;

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

import com.elicitsoftware.security.ServiceToken;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-020: View the System Overview, step 5 (BR-163) — a console whose service token is still the
 * development value lists it as outstanding setup work, naming the variable and never the value;
 * a console with a token of its own does not.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SetupWarningsServiceTokenTest {

    private static final String WARNING = "The service token is the development value. Generate one for this site"
            + " (openssl rand -hex 32) and set ELICIT_SERVICE_TOKEN to it on every Elicit service.";

    @Inject
    DefaultAccountCheck defaultAccounts;

    @Inject
    SurveyDefinitionPresenceCheck surveyPresence;

    @Inject
    RequiredConfigCheck requiredConfig;

    @Inject
    SetupWarnings configured;

    private List<SetupWarnings.Warning> warningsWith(String token) {
        SetupWarnings warnings = new SetupWarnings();
        warnings.defaultAccounts = defaultAccounts;
        warnings.surveyPresence = surveyPresence;
        warnings.requiredConfig = requiredConfig;
        warnings.serviceToken = new ServiceToken(Optional.ofNullable(token), Optional.empty(), "http://survey:8080");
        return warnings.warnings();
    }

    @Test
    @TestTransaction
    void given_theDevelopmentToken_when_theSetupWorkIsListed_then_itIsAnItemWithoutTheValue() {
        List<SetupWarnings.Warning> warnings = warningsWith(ServiceToken.DEVELOPMENT_VALUE);

        assertTrue(warnings.contains(new SetupWarnings.Warning(WARNING, SetupWarnings.Remedy.NONE)), warnings.toString());
        assertTrue(warnings.stream().noneMatch(w -> w.text().contains(ServiceToken.DEVELOPMENT_VALUE)));
    }

    @Test
    @TestTransaction
    void given_aTokenOfItsOwnOrNone_when_theSetupWorkIsListed_then_thereIsNoSuchItem() {
        assertEquals(0, warningsWith("a-site-service-token-0123456789abcdef").stream()
                .filter(w -> w.text().equals(WARNING)).count());
        assertEquals(0, warningsWith(null).stream().filter(w -> w.text().equals(WARNING)).count());
        assertEquals(0, configured.warnings().stream().filter(w -> w.text().equals(WARNING)).count(),
                "the test profile's token is not the development value");
    }
}
