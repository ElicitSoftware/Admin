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

import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.ImportClient;
import com.elicitsoftware.model.User;
import com.elicitsoftware.service.ImportClientService.NameConflict;
import com.elicitsoftware.test.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Booted persistence tests for {@link ImportClientService} and {@link ImportClient} (UC-032:
 * Manage Import Clients) against the schema of {@code V0.0.25__Add_Import_Clients.sql}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ImportClientServiceTest {

    @Inject
    ImportClientService service;

    private static String token() {
        return Long.toString(System.nanoTime(), 36);
    }

    private Department department(String token) {
        Department department = new Department();
        department.name = "Import Client Dept " + token;
        department.code = "IC-" + token;
        department.defaultMessageId = "1";
        department.fromEmail = "import-client@example.org";
        department.persist();
        return department;
    }

    private ImportClient client(String name, Department... departments) {
        ImportClient client = new ImportClient();
        client.setName(name);
        client.setActive(true);
        client.setDepartments(new HashSet<>(Set.of(departments)));
        return client;
    }

    /** UC-032 steps 4-5: a new import client is stored with its description and departments. */
    @Test
    @TestTransaction
    void given_aNewImportClient_when_saved_then_itIsStoredWithItsDepartments() {
        String token = token();
        Department first = department(token + "A");
        Department second = department(token + "B");
        ImportClient client = client("service-account-" + token, first, second);
        client.setDescription("Registration feed");

        service.save(client);
        ImportClient.getEntityManager().flush();
        ImportClient.getEntityManager().clear();

        ImportClient stored = ImportClient.findByName("service-account-" + token);
        assertNotNull(stored);
        assertTrue(stored.getId() > 0);
        assertEquals("Registration feed", stored.getDescription());
        assertTrue(stored.isActive());
        assertEquals(Set.of(first.id, second.id),
                Set.of(stored.getDepartments().stream().map(d -> d.id).toArray()));
    }

    /** UC-032 A3: an existing import client is changed in place: deactivated, departments replaced. */
    @Test
    @TestTransaction
    void given_anExistingImportClient_when_changedAndSaved_then_theRecordFollows() {
        String token = token();
        Department first = department(token + "A");
        Department second = department(token + "B");
        ImportClient client = service.save(client("service-account-" + token, first));
        ImportClient.getEntityManager().flush();
        ImportClient.getEntityManager().clear();

        ImportClient edited = new ImportClient();
        edited.setId(client.getId());
        edited.setName("service-account-" + token);
        edited.setActive(false);
        edited.setDepartments(new HashSet<>(Set.of(second)));
        service.save(edited);
        ImportClient.getEntityManager().flush();
        ImportClient.getEntityManager().clear();

        ImportClient stored = ImportClient.findById(client.getId());
        assertFalse(stored.isActive());
        assertEquals(1, stored.getDepartments().size());
        assertEquals(second.id, stored.getDepartments().iterator().next().id);
        assertEquals(1, ImportClient.count("name = ?1", "service-account-" + token));
    }

    /** UC-032 A4, BR-169: deleting removes the record and its assignments, and the departments stay. */
    @Test
    @TestTransaction
    void given_anImportClientWithDepartments_when_deleted_then_theRecordAndItsAssignmentsAreGone() {
        String token = token();
        Department department = department(token);
        ImportClient client = service.save(client("service-account-" + token, department));
        ImportClient.getEntityManager().flush();

        assertTrue(service.delete(client.getId()));
        ImportClient.getEntityManager().flush();
        ImportClient.getEntityManager().clear();

        assertNull(ImportClient.findByName("service-account-" + token));
        assertEquals(0L, ((Number) ImportClient.getEntityManager()
                .createNativeQuery("select count(*) from survey.import_client_departments where import_client_id = ?1")
                .setParameter(1, client.getId()).getSingleResult()).longValue());
        assertNotNull(Department.findById(department.id), "the department is not the client's to remove");
        assertFalse(service.delete(client.getId()), "a second delete finds nothing");
    }

    /** UC-032 BR-165: the name is unique among import clients, at the database as well. */
    @Test
    @TestTransaction
    void given_aNameAnImportClientHolds_when_anotherIsPersisted_then_theDatabaseRefusesIt() {
        String name = "service-account-" + token();
        service.save(client(name));
        ImportClient.getEntityManager().flush();

        assertThrows(PersistenceException.class, () -> {
            client(name).persist();
            ImportClient.getEntityManager().flush();
        });
    }

    /** UC-032 A2, UC-008 A2, BR-166: a name is a user or an import client, never both. */
    @Test
    @TestTransaction
    void given_namesHeldByAUserAndByAnImportClient_when_askedForEitherKind_then_theHolderIsNamed() {
        String token = token();
        User user = new User();
        user.setUsername("person." + token);
        user.setFirstName("Per");
        user.setLastName("Son");
        user.setActive(true);
        user.persistAndFlush();
        ImportClient client = service.save(client("service-account-" + token));
        ImportClient.getEntityManager().flush();

        // On the Import clients page
        assertEquals(NameConflict.USER, service.conflictForImportClient("person." + token, 0));
        assertEquals(NameConflict.IMPORT_CLIENT, service.conflictForImportClient("service-account-" + token, 0));
        assertEquals(NameConflict.NONE, service.conflictForImportClient("service-account-" + token, client.getId()),
                "an import client keeps its own name");
        assertEquals(NameConflict.NONE, service.conflictForImportClient("free." + token, 0));

        // On the Users screen
        assertEquals(NameConflict.IMPORT_CLIENT, service.conflictForUser("service-account-" + token, 0));
        assertEquals(NameConflict.USER, service.conflictForUser("person." + token, 0));
        assertEquals(NameConflict.NONE, service.conflictForUser("person." + token, user.getId()),
                "a user keeps their own name");
        assertEquals(NameConflict.NONE, service.conflictForUser("free." + token, 0));
    }

    /** UC-032: two records are the same import client when their ids match. */
    @Test
    void given_twoImportClients_when_compared_then_identityIsTheId() {
        ImportClient a = new ImportClient();
        a.setId(7);
        ImportClient b = new ImportClient();
        b.setId(7);
        ImportClient c = new ImportClient();
        c.setId(8);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c));
        assertFalse(a.equals("service-account-x"));
    }
}
