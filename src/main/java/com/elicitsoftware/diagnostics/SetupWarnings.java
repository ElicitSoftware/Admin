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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.MessageTemplate;
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
        USERS, APPLY_SURVEY_DEFINITION, DEPARTMENTS, MESSAGE_TEMPLATES, NONE
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

    public SetupWarnings() {
        // CDI managed bean
    }

    @Transactional
    public List<Warning> warnings() {
        List<Warning> warnings = new ArrayList<>();

        List<String> accounts = defaultAccounts.findDefaultAccounts();
        if (!accounts.isEmpty()) {
            warnings.add(new Warning(DefaultAccountCheck.instruction(accounts), Remedy.USERS));
        }
        if (!surveyPresence.isSurveyInstalled()) {
            warnings.add(new Warning("No survey is installed. Apply a survey definition before registering subjects.",
                    Remedy.APPLY_SURVEY_DEFINITION));
        }
        if (Department.count("fromEmail is not null and fromEmail <> ''") == 0) {
            warnings.add(new Warning("No department has a sender address, so invitations have no From address.",
                    Remedy.DEPARTMENTS));
        }
        if (MessageTemplate.count() > 0 && MessageTemplate.count("message like ?1", "%<ACCESS_CODE>%") == 0) {
            warnings.add(new Warning("No message template contains the <ACCESS_CODE> placeholder, so no invitation"
                    + " can carry a survey link.", Remedy.MESSAGE_TEMPLATES));
        }
        for (RequiredConfigCheck.LegacySetting legacy : requiredConfig.legacySettingsInUse()) {
            warnings.add(new Warning("The setting " + legacy.property() + " is configured but no longer read; use "
                    + legacy.replacement() + " instead.", Remedy.NONE));
        }
        return warnings;
    }
}
