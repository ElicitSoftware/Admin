package com.elicitsoftware.service;

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

import com.elicitsoftware.model.ImportClient;
import com.elicitsoftware.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Application-scoped service that owns persistence for {@link ImportClient} records (UC-032).
 */
@ApplicationScoped
public class ImportClientService {

    /**
     * Why a name cannot be given to a record (BR-166): a name is a user or an import client,
     * never both, and is unique among its own kind.
     */
    public enum NameConflict {
        /** No other record holds the name. */
        NONE,
        /** A user holds the name. */
        USER,
        /** An import client holds the name. */
        IMPORT_CLIENT
    }

    /**
     * Default constructor for CDI.
     */
    public ImportClientService() {
        // Default constructor for CDI
    }

    /**
     * Persists a new import client or merges changes into an existing one.
     *
     * @param client the import client to save; id 0 is treated as new
     * @return the managed, saved instance
     */
    @Transactional
    public ImportClient save(ImportClient client) {
        if (client.getId() == 0) {
            client.persist();
            return client;
        }
        return ImportClient.getEntityManager().merge(client);
    }

    /**
     * Removes an import client and its department assignments (BR-169). What the client
     * registered is not touched.
     *
     * @param id the import client's id
     * @return true when a record was removed
     */
    @Transactional
    public boolean delete(long id) {
        ImportClient client = ImportClient.findById(id);
        if (client == null) {
            return false;
        }
        // The join table is owned by the entity, so removing it removes its rows.
        client.delete();
        return true;
    }

    /**
     * Whether an import client may take a name (BR-166).
     *
     * @param name the name to be saved
     * @param ownId the id of the import client being saved, 0 for a new one
     * @return who else holds the name, if anyone
     */
    @Transactional
    public NameConflict conflictForImportClient(String name, long ownId) {
        if (User.count("username = ?1", name) > 0) { // i18n:ignore (JPQL)
            return NameConflict.USER;
        }
        ImportClient other = ImportClient.findByName(name);
        return other != null && other.getId() != ownId ? NameConflict.IMPORT_CLIENT : NameConflict.NONE;
    }

    /**
     * Whether a user may take a username (BR-166).
     *
     * @param username the username to be saved
     * @param ownId the id of the user being saved, 0 for a new one
     * @return who else holds the name, if anyone
     */
    @Transactional
    public NameConflict conflictForUser(String username, long ownId) {
        if (ImportClient.findByName(username) != null) {
            return NameConflict.IMPORT_CLIENT;
        }
        User other = User.find("username = ?1", username).firstResult(); // i18n:ignore (JPQL)
        return other != null && other.getId() != ownId ? NameConflict.USER : NameConflict.NONE;
    }
}
