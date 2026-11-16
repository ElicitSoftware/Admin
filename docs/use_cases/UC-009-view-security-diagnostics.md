# Use Case: View Security Diagnostics

## Overview

**Use Case ID:** UC-009
**Use Case Name:** View Security Diagnostics
**Primary Actor:** Survey User
**Goal:** Inspect the current sign-in's authentication and authorization state to troubleshoot access and role problems.
**Status:** Implemented

## Preconditions

- The user is authenticated and holds the `elicit_user` or `elicit_admin` role (UC-001).

## Main Success Scenario

1. The user opens the diagnostics view, either from the OIDC entry of the System section (administrators, FR-026) or by its address.
2. The system displays the current identity's details: principal name, whether the identity is anonymous, the full set of roles, whether roles came from the identity provider or the database, the checks for the admin and user roles, and the identity and access tokens, masked to their last four characters (BR-142).
3. The user reads the information to diagnose an access issue.

## Alternative Flows

### A1: Token not available

**Trigger:** The identity or access token cannot be resolved (step 2).
**Flow:**

1. The system shows a "not available" indicator for that item instead of failing.

## Postconditions

### Success Postconditions

- The user has seen the effective authentication and authorization state; no data is changed.

### Failure Postconditions

- None; the view is read-only.

## Business Rules

### BR-033: Role source is visible

The diagnostics view reports whether the effective roles were supplied by the OIDC provider or the local database fallback (see UC-001).

### BR-142: Raw tokens are shown in development mode only

The identity and access tokens are shown masked to their last four characters. The setting `elicit.debug.reveal-tokens` shows them in full only when the application runs in development mode; in a deployed image the tokens stay masked whatever the setting says, and the System overview lists the ignored setting among its outstanding items (UC-020).

---

## Reference

Derived from `DebugView` and `RoleSecurityIdentityAugmentor`. The navigation entry (FR-026) is the OIDC
item of the System section built by `MainLayout`; the view itself is unchanged by it.
