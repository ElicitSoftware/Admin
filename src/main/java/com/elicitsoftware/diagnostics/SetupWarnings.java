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

import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.ConfigProvider;
import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.MessageTemplate;
import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.security.ServiceToken;
import com.elicitsoftware.service.DefaultAccountCheck;
import com.elicitsoftware.service.SurveyDefinitionPresenceCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The setup work still outstanding in this deployment (UC-020 step 5), re-established on every
 * call (BR-083).
 */
@ApplicationScoped
public class SetupWarnings {

    /** Where a warning is resolved. */
    public enum Remedy {
        USERS, APPLY_SURVEY_DEFINITION, DEPARTMENTS, MESSAGE_TEMPLATES, BRANDING, NONE
    }

    /**
     * One outstanding item.
     *
     * @param text   what is wrong and what to do
     * @param remedy the screen that resolves it
     */
    public record Warning(String text, Remedy remedy) {
    }

    @Inject
    DefaultAccountCheck defaultAccounts;

    @Inject
    SurveyDefinitionPresenceCheck surveyPresence;

    @Inject
    RequiredConfigCheck requiredConfig;

    @Inject
    ServiceToken serviceToken;

    @Inject
    BrandDiagnostics brand;

    /** Creates the bean; CDI injects its checks. */
    public SetupWarnings() {
        // CDI managed bean
    }

    /**
     * Runs every setup check against the current database and configuration (BR-083).
     *
     * @return the outstanding items, in the order the checks run; empty when setup is complete
     */
    @Transactional
    public List<Warning> warnings() {
        List<Warning> warnings = new ArrayList<>();

        List<String> accounts = defaultAccounts.findDefaultAccounts();
        if (!accounts.isEmpty()) {
            warnings.add(new Warning(instruction(accounts), Remedy.USERS));
        }
        if (!surveyPresence.isSurveyInstalled()) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.noSurvey"),
                    Remedy.APPLY_SURVEY_DEFINITION));
        }
        // A fresh deployment ships no department (UC-028 C-016); that is the item to fix first,
        // and the sender-address check only means something once a department exists.
        if (Department.count() == 0) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.noDepartment"),
                    Remedy.DEPARTMENTS));
        } else if (Department.count("fromEmail is not null and fromEmail <> ''") == 0) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.noSenderAddress"),
                    Remedy.DEPARTMENTS));
        }
        // Likewise no message template is seeded; without one, registration builds no invitation.
        if (MessageTemplate.count() == 0) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.noMessageTemplate"),
                    Remedy.MESSAGE_TEMPLATES));
        } else if (MessageTemplate.count("message like ?1", "%<ACCESS_CODE>%") == 0) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.noAccessCodeTemplate"),
                    Remedy.MESSAGE_TEMPLATES));
        }
        // UC-020 BR-163: the token the compose stack supplies is public knowledge.
        if (serviceToken.isDevelopmentValue()) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.devServiceToken"), Remedy.NONE));
        }
        for (RequiredConfigCheck.LegacySetting legacy : requiredConfig.legacySettingsInUse()) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.legacySetting",
                    legacy.property(), legacy.replacement()), Remedy.NONE));
        }
        // Umbrella UC-014 BR-008, BR-009: a brand's logo mistakes are otherwise visible only in the header.
        for (String brandWarning : brand.warnings()) {
            warnings.add(new Warning(brandWarning, Remedy.BRANDING));
        }
        boolean revealTokens = ConfigProvider.getConfig()
                .getOptionalValue("elicit.debug.reveal-tokens", Boolean.class).orElse(false);
        if (revealTokensIgnored(revealTokens, LaunchMode.current())) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.revealTokensIgnored"), Remedy.NONE));
        }
        return warnings;
    }

    /**
     * Whether the token-reveal setting is on where it has no effect (UC-009 BR-142): it is
     * honored in development mode only.
     */
    static boolean revealTokensIgnored(boolean configured, LaunchMode mode) {
        return configured && mode != LaunchMode.DEVELOPMENT;
    }

    /**
     * The seeded-account instruction in the current language; the English wording the startup
     * log uses stays in {@link DefaultAccountCheck#instruction(List)}.
     *
     * @param accounts the usernames of the seeded accounts still present
     * @return the instruction naming those accounts
     */
    public static String instruction(List<String> accounts) {
        String names = accounts.stream().map(a -> "'" + a + "'")
                .reduce((a, b) -> Translations.get("common.listAnd", a, b)).orElse("");
        return Translations.get(accounts.size() > 1
                ? "defaultAccountNotice.instruction.many" : "defaultAccountNotice.instruction.one", names);
    }
}
