# Use Case: Warn About Default Accounts

## Overview

**Use Case ID:** UC-021
**Use Case Name:** Warn About Default Accounts
**Primary Actor:** Survey Administrator
**Goal:** Learn, from the service log and from the console itself, that the accounts seeded by the database migrations still exist, and be told to rename them before the deployment goes live.
**Status:** Tested

## Preconditions

- The database migrations have run. The dev-data migration seeds two accounts in `survey.users`, with usernames `admin` and `user`, so that a fresh deployment can be signed into.

## Main Success Scenario

1. The service starts and checks whether any account named `admin` or `user` exists.
2. Finding one or both, the system writes one warning to the service log naming the accounts found and instructing the operator to rename them.
3. An administrator opens the admin console.
4. The system re-checks the accounts and, finding one still present, shows a warning on every console screen naming the accounts and offering the Users screen (UC-008) as the place to rename them.
5. The System Overview (UC-020) lists the same accounts among the outstanding setup work.
6. The administrator renames the accounts, and where the identity provider is the source of usernames, renames the matching identities there as well.
7. On the next screen the administrator opens, the warning is gone.

## Alternative Flows

### A1: No default account exists

**Trigger:** Neither seeded username is present (step 1 or step 4).
**Flow:**

1. The system logs nothing at warning level and shows no banner.
2. Use case ends.

### A2: The signed-in user is not an administrator

**Trigger:** The console user holds `elicit_user` only (step 4).
**Flow:**

1. The system shows no banner to that user; only administrators can act on it.
2. Use case ends.

### A3: The administrator continues without renaming

**Trigger:** The administrator navigates elsewhere (step 6).
**Flow:**

1. The warning stays on every screen until the accounts are renamed.
2. Use case continues at step 6 whenever the administrator returns.

## Postconditions

### Success Postconditions

- The log carries one warning per service start while a seeded account exists.
- While a seeded account exists, every console screen an administrator opens carries the warning.
- Once both accounts are renamed, neither the banner nor the overview item appears, without a restart.
- This use case changes no stored data; renaming happens through UC-008.

### Failure Postconditions

- The seeded accounts remain and nothing tells the operator; a well-known username stays usable.

## Business Rules

### BR-085: The check is by username, not by id

The seeded accounts are recognised by the usernames `admin` and `user` exactly, whether or not the rows are active, because the migration looks them up the same way. Renaming is the remedy; deactivating them leaves the warning in place.

### BR-086: The warning reflects what is stored now

Whether a seeded account exists is re-established as the console is used, not captured once at start (BR-076). Renaming takes effect on the next screen without a restart.

### BR-087: The warning explains, it never blocks

Every screen stays usable while the warning is shown (BR-077). The seeded accounts are the only way into a fresh deployment, so the console cannot refuse them.

### BR-088: The instruction covers both stores

Under `elicit.authorization.mode=DATABASE`, roles are looked up by the username the identity provider presents, so the instruction tells the operator to rename the account in the identity provider as well as in the console. Under OIDC mode the console record is renamed alone.

---

## Reference

Traces to FR-021. Follows the shape of UC-019: a startup check that logs, and a per-navigation
check that the layout renders as a banner. The seeded accounts come from
`V0.0.3__POPULATE_DEV_DATA.sql`; their role grants from `V0.0.11__Seed_Admin_And_User_Roles.sql`,
which also looks them up by username.

Implemented by `DefaultAccountCheck` (the startup warning, step 2, and the per-navigation answer,
BR-086), `DefaultAccountNotice` (the wording and the Users link) and `MainLayout.showRouterLayoutContent`
(the banner, step 4); the overview item comes from `SetupWarnings`. Verified by `DefaultAccountCheckTest`
and `DefaultAccountWarningTest`.
