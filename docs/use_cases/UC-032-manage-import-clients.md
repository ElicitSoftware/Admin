# Use Case: Manage Import Clients

## Overview

**Use Case ID:** UC-032
**Use Case Name:** Manage Import Clients
**Primary Actor:** Survey Administrator
**Goal:** Record the machine clients of the registration API and the departments each may register into, apart from the people who use the console.
**Status:** Implemented

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).
- The client exists in the identity provider and holds the `elicit_importer` role there and no other Elicit role.

## Main Success Scenario

1. The administrator opens Import clients, beside Users in the navigation, and sees the list of import clients with name, description, active flag and assigned departments.
2. The administrator chooses to add an import client or to edit one.
3. The administrator enters or changes the name, the description, the active flag and the set of assigned departments.
4. The administrator saves.
5. The system stores the import client and its departments, then returns to the list.

## Alternative Flows

### A1: Import client not found

**Trigger:** The administrator opens an edit link for an import client that no longer exists.
**Flow:**

1. The system reports that the import client was not found and returns to the list.

### A2: The name is taken

**Trigger:** The administrator saves a name that another import client holds, or that a user holds (step 4).
**Flow:**

1. The system says which of the two holds the name, stores nothing and leaves the form open (BR-166).

### A3: Deactivate an import client

**Trigger:** The administrator clears the active flag and saves (step 3).
**Flow:**

1. The system stores the record as inactive. The client's next request to the registration API is refused (BR-167); its record and departments are kept.

### A4: Delete an import client

**Trigger:** The administrator chooses Delete on an existing import client.
**Flow:**

1. The system asks for confirmation, naming the import client.
2. On confirmation the system removes the import client and its department assignments and returns to the list. The subjects the client registered are not touched (BR-169).
3. If the administrator cancels, nothing changes.

### A5: Not an administrator

**Trigger:** A user without the `elicit_admin` role opens the page or its edit form.
**Flow:**

1. The system denies access (UC-001 A2). The navigation does not offer the page.

## Postconditions

### Success Postconditions

- The import client record and its department assignments reflect the administrator's changes.
- The registration API admits the client for exactly the departments of its active record (UC-010 BR-137, BR-170).

### Failure Postconditions

- No change is stored.

## Business Rules

### BR-165: The name is the principal the client's tokens carry

An import client's name is matched against the principal name of a bearer token. For a Keycloak service account that is `service-account-<client id>`. The name is required, at most 100 characters and unique among import clients.

### BR-166: A name is a user or an import client, never both

A name that a user holds (UC-008) is refused here, as an import client's name is refused there.

### BR-167: New import clients are active; an inactive one registers nowhere

A newly created import client is active unless the administrator clears the flag. An inactive import client is refused by the registration API as if it had no record (UC-010 BR-170).

### BR-168: An import client record gives no access to the console

The record assigns departments for the registration API and nothing else. It grants no role, and a principal that matches one and arrives in a browser session is treated as having no user record (UC-001 BR-171).

### BR-169: Deleting removes the record and its assignments only

Deleting an import client removes its record and its department assignments. Subjects, respondents and messages it registered remain.

---

## Reference

Derived from `ImportClientsView`, `EditImportClientView`, `ImportClient`, `ImportClientService`, `DepartmentScope`, and the migration `V0.0.25__Add_Import_Clients.sql` (`survey.import_clients`, `survey.import_client_departments`). The role `elicit_importer` itself is assigned in the identity provider, not here; see UC-010 for what the record admits and UC-016 for the roles a user can hold.
