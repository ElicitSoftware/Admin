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

1. The user opens the diagnostics view.
2. The system displays the current identity's details: principal name, whether the identity is anonymous, the full set of roles, whether roles came from the identity provider or the database, the checks for the admin and user roles, and the raw identity and access tokens.
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

---

## Reference

Derived from `DebugView` and `RoleSecurityIdentityAugmentor`.
