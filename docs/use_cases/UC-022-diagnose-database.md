# Use Case: Diagnose Database

## Overview

**Use Case ID:** UC-022
**Use Case Name:** Diagnose Database
**Primary Actor:** Survey Administrator
**Goal:** Confirm that both database connections work and see which migration versions each module has applied, so that a credential, ordering or missing-schema problem can be told apart.
**Status:** Tested

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).

## Main Success Scenario

1. The administrator opens Database from the System section.
2. For each of the two connections the service holds (the application connection and the owner connection that runs migrations), the system connects, runs a trivial query, and shows the connected user, the server version, and the round-trip time.
3. The system shows the migration history of each module that shares the schema (Survey, Admin, Family History), giving the latest version applied, when, and whether it succeeded.
4. The system reports whether the durable-key sequences introduced by the Kimball upgrade exist, since the Admin and Family History migrations depend on them.
5. The system shows how many surveys and how many console users are stored, and whether the seeded default accounts are among them (UC-021).
6. The administrator reads the result to locate the problem.

## Alternative Flows

### A1: A connection fails

**Trigger:** Connecting or the trivial query fails on one connection (step 2).
**Flow:**

1. The system marks that connection failed with the driver's message, and names the setting that supplies its password as present or absent (BR-082).
2. Use case continues at step 3 with whatever the other connection can answer.

### A2: A module's migration table does not exist

**Trigger:** A history table is absent (step 3).
**Flow:**

1. The system reports that module as not yet installed rather than failing the screen.
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- The administrator has seen the state of both connections and the applied migrations; no data is changed.

### Failure Postconditions

- None; the screen is read-only.

## Business Rules

### BR-089: Both connections are checked

The readiness endpoint probes the application connection only. This screen also probes the owner connection, because a wrong owner password is invisible to readiness and surfaces only as a failed migration at the next start.

### BR-090: Migration state is read, never changed

The screen reads the history tables. Migrating, repairing or baselining stays with the modules that own the migrations (C-004).

---

## Reference

Traces to FR-022. The upgrade dependencies it reports are the ones described for the Kimball
Type 2 upgrade in the umbrella `DeploymentScript.md`.

Implemented by `DatabaseDiagnostics` (both connections through the default and `owner` datasources,
the three Flyway history tables, the durable-key sequence, and the counts) and `SystemDatabaseView`.
Verified by `DatabaseDiagnosticsTest` and `SystemViewsRenderTest`.
