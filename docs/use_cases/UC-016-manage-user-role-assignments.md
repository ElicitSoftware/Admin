# Use Case: Manage User Role Assignments

## Overview

**Use Case ID:** UC-016
**Use Case Name:** Manage User Role Assignments
**Primary Actor:** Survey Administrator
**Goal:** Directly grant or clear a single database-fallback role for a user, when the deployment is configured to source roles from the database rather than relying solely on OIDC.

**Status:** Implemented

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).
- `elicit.authorization.mode=DATABASE`.

## Main Success Scenario

1. The administrator opens Edit User for a user (UC-008).
2. Because the deployment is in database mode, the system shows a Database Role Assignment section with a heading, explanatory text, and a single-select role dropdown, pre-populated with the user's current raw grant, if any.
3. The administrator selects a role, or clears the current selection.
4. The administrator saves.
5. The system sets, replaces, or clears the user's single grant in the local role table to match the selection, then returns to the list.

## Alternative Flows

### A1: OIDC mode

**Trigger:** `elicit.authorization.mode=OIDC` (step 2).
**Flow:**

1. The Database Role Assignment section is not shown.
2. Role resolution for this user continues to rely solely on OIDC; no database consultation occurs (UC-001, BR-005).

### A2: New user

**Trigger:** The administrator is creating a new user rather than editing an existing one (step 2).
**Flow:**

1. The Database Role Assignment section is shown with no pre-populated selection, since a new user has no id until first saved.
2. The role selection is applied only after the user record itself is persisted and an id is assigned.

## Postconditions

### Success Postconditions

- The user's local role table entry reflects the administrator's selection: exactly one row if a role was chosen, or none if cleared.

### Failure Postconditions

- No change is stored.

## Business Rules

### BR-054: Recognized roles enforced at two layers

Only `elicit_admin`, `elicit_user`, and `elicit_importer` may be granted. This is enforced both by the service layer and by a database check constraint on `survey.user_roles.role_name`.

### BR-055: At most one raw grant per user

A user holds at most one raw role grant at a time. This is enforced by the service, which deletes any existing grant before inserting the selected one; no additional schema uniqueness beyond the existing composite primary key is needed.

### BR-056: Administrator picks the highest role only

The administrator selects a user's single highest role. The cumulative role hierarchy (UC-001, BR-006) fills in the implied roles at resolution time; the dropdown never needs to represent more than one selection.

### BR-057: Grants require an existing user

A role grant references an existing user record. Grants are not cascaded from any user-deletion flow, since deleting users is not supported (UC-008).

---

## Reference

Derived from `EditUserView`, `UserRoleService`, `UserRole`, `AuthorizationModeConfig`, and `ElicitRoles`, and the migrations `V0.0.10__Add_User_Roles_Check_Constraint.sql` and `V0.0.11__Seed_Admin_And_User_Roles.sql`. See UC-008 for the rest of the user record and UC-001 for role resolution and the cumulative expansion rules.
