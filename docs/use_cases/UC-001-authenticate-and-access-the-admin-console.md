# Use Case: Authenticate and Access the Admin Console

## Overview

**Use Case ID:** UC-001
**Use Case Name:** Authenticate and Access the Admin Console
**Primary Actor:** Survey User
**Supporting Actor:** OIDC Provider
**Goal:** Sign in through the organization's identity provider and reach the admin console with the roles that govern what the user may do.
**Status:** Implemented

## Preconditions

- The user has an account in the configured OIDC provider (Keycloak in test; any OIDC-compliant server in production).
- The service is reachable and the OIDC provider is online.

## Main Success Scenario

1. The user opens any protected page of the admin console.
2. The system detects the user is not authenticated and redirects to the OIDC provider.
3. The user authenticates with the OIDC provider.
4. The OIDC provider returns the user to the console callback with an identity.
5. The system determines the user's effective roles: if the OIDC identity already carries an Elicit role, that role is used; otherwise, when `elicit.authorization.mode=DATABASE`, the system looks up a role for the username in the local role table (in the default `OIDC` mode, no database lookup occurs and the identity is granted no role). Whichever raw role is found, from either source, is expanded to its full implied set (BR-006) before being granted.
6. The system restores the page the user originally requested and displays it.

## Alternative Flows

### A1: Authenticated but no application user record

**Trigger:** The identity is valid but no active user record exists for the username (step 5).
**Flow:**

1. The system shows a message explaining the user is authenticated but has no active application account, and to contact an administrator.
2. No subject data is shown.

### A2: Authenticated but lacks the required role for a page

**Trigger:** An authenticated user requests a page whose role they do not hold.
**Flow:**

1. The system denies access and shows an access-denied / unauthorized page instead of the requested view.

### A3: Roles supplied by the database fallback

**Trigger:** The OIDC identity carries none of the Elicit roles and `elicit.authorization.mode=DATABASE` (step 5).
**Flow:**

1. The system reads the user's role from the local role table for the active username.
2. The effective identity is stamped as database-sourced and granted that role, expanded to its implied set (BR-006).
3. The main flow continues.

## Postconditions

### Success Postconditions

- The user has an authenticated session with a resolved set of Elicit roles.
- The effective identity records whether roles came from OIDC or the database.

### Failure Postconditions

- No authenticated session is established, or the user is shown an access-denied / no-account message and cannot act on data.

## Business Rules

### BR-001: Recognized application roles

The application recognizes exactly three roles: `elicit_admin`, `elicit_user`, and `elicit_importer`. Any other role carried by the identity provider is ignored when deciding whether database role fallback is needed.

### BR-002: OIDC is the primary authority for roles

Roles from the OIDC provider take precedence. The local role table is consulted only when the identity carries none of the recognized application roles.

### BR-003: Only active users may sign in to act

Database role lookup returns roles only for a user whose account is marked active. A user with no active application record cannot operate on data.

### BR-004: Public endpoints

Logout, health checks, metrics, brand static resources, the diagnostic test and roles endpoints, and PDF downloads are reachable without authentication; every other path requires an authenticated session.

### BR-005: Database fallback is gated by authorization mode

The local role table is consulted only when `elicit.authorization.mode=DATABASE`. In the default `OIDC` mode, an identity that carries none of the recognized application roles is granted no role, and no database lookup occurs.

### BR-006: Roles are cumulative

Roles imply a hierarchy, independent of source: `elicit_admin` implies `elicit_user` and `elicit_importer`; `elicit_user` implies `elicit_importer`; `elicit_importer` implies only itself. This expansion applies uniformly to roles resolved from OIDC and from the database fallback. Only the raw (unexpanded) role is ever stored in Keycloak or in the local role table; the expanded set exists only at resolution time.

---

## Reference

Derived from `application.properties` (OIDC and HTTP permission configuration), `RoleSecurityIdentityAugmentor`, `UserRoleDatabaseLookup`, `AuthorizationModeConfig`, `ElicitRoles`, `LoginView`, `LogoutView`, `UnauthorizedView`, and `AccessDeniedErrorView`.
