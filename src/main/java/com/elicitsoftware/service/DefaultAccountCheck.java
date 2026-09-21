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

import com.elicitsoftware.model.User;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Warns while the accounts seeded by the database migrations still exist (UC-021).
 * <p>
 * {@code V0.0.3__POPULATE_DEV_DATA.sql} seeds {@code admin} and {@code user} in
 * {@code survey.users} so that a fresh deployment can be signed into, and
 * {@code V0.0.11__Seed_Admin_And_User_Roles.sql} grants their roles by looking them up by
 * username. This check recognises them the same way (BR-085): by username, whether or not
 * the row is active.
 */
@ApplicationScoped
public class DefaultAccountCheck {

    /** The usernames the migrations seed. */
    public static final List<String> SEEDED_USERNAMES = List.of("admin", "user");

    public DefaultAccountCheck() {
        // CDI managed bean
    }

    /**
     * The seeded usernames that still exist, re-read on every call so the console reflects a
     * rename without a restart (BR-086).
     *
     * @return the seeded usernames still present, in the order they were seeded; empty when none
     */
    @Transactional
    public List<String> findDefaultAccounts() {
        List<String> present = User.<User>find("username in ?1", SEEDED_USERNAMES).list().stream()
                .map(User::getUsername)
                .toList();
        return SEEDED_USERNAMES.stream().filter(present::contains).toList();
    }

    /**
     * Builds the instruction shown in the log and the console (BR-088).
     *
     * @param accounts the seeded usernames found
     * @return the instruction, or an empty string when there is nothing to warn about
     */
    public static String instruction(List<String> accounts) {
        if (accounts.isEmpty()) {
            return "";
        }
        String names = accounts.stream().map(a -> "'" + a + "'").reduce((a, b) -> a + " and " + b).orElse("");
        return "The database migrations seeded the default account" + (accounts.size() > 1 ? "s " : " ") + names
                + ". Rename " + (accounts.size() > 1 ? "them" : "it")
                + " before this deployment goes live: open Admin > Users and change the username, and when"
                + " elicit.authorization.mode=DATABASE rename the matching account in the identity provider"
                + " to the same value.";
    }

    /**
     * Logs one warning per service start while a seeded account exists (UC-021 step 2).
     * <p>
     * {@code @Startup} sits on the method rather than the class so it runs through the
     * {@code @Transactional} interceptor, as {@link SurveyDefinitionPresenceCheck} does.
     */
    @Startup
    @Transactional
    void warnWhenDefaultAccountsPresent() {
        List<String> accounts = findDefaultAccounts();
        if (accounts.isEmpty()) {
            Log.debug("No seeded default accounts are present in survey.users");
        } else {
            Log.warn(instruction(accounts));
        }
    }
}
