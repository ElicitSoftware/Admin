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
import com.elicitsoftware.admin.i18n.Translations;
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
        for (RequiredConfigCheck.LegacySetting legacy : requiredConfig.legacySettingsInUse()) {
            warnings.add(new Warning(Translations.get("systemOverviewView.warning.legacySetting",
                    legacy.property(), legacy.replacement()), Remedy.NONE));
        }
        return warnings;
    }

    /**
     * The seeded-account instruction in the current language; the English wording the startup
     * log uses stays in {@link DefaultAccountCheck#instruction(List)}.
     */
    public static String instruction(List<String> accounts) {
        String names = accounts.stream().map(a -> "'" + a + "'")
                .reduce((a, b) -> Translations.get("common.listAnd", a, b)).orElse("");
        return Translations.get(accounts.size() > 1
                ? "defaultAccountNotice.instruction.many" : "defaultAccountNotice.instruction.one", names);
    }
}
