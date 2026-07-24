package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Application-scoped service that owns persistence for {@link User} entities.
 *
 * <p>Keeping the transactional data-access logic here (rather than in the Vaadin view) lets the
 * UI layer stay focused on presentation, and gives a single, testable seam for user
 * create/update behaviour.</p>
 *
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see User
 */
@ApplicationScoped
public class UserService {

    /**
     * Default constructor for CDI.
     */
    public UserService() {
        // Default constructor for CDI
    }

    /**
     * Persists a new user or merges changes into an existing one, in a single transaction.
     *
     * <p>A user whose id is {@code 0} is treated as new and inserted; any other id is treated as
     * an update and merged.</p>
     *
     * @param user the user to save; must not be {@code null}
     * @return the managed, saved user instance
     */
    @Transactional
    public User save(User user) {
        if (user.getId() == 0) {
            user.persist();
            return user;
        }
        return User.getEntityManager().merge(user);
    }
}
