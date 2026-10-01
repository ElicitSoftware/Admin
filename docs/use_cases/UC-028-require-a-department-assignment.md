# Use Case: Require a Department Assignment

## Overview

**Use Case ID:** UC-028
**Use Case Name:** Require a Department Assignment
**Primary Actor:** Survey Administrator
**Secondary Actor:** Survey User
**Goal:** Be told, before touching any screen, that my account is not assigned to a department, and either be given the one action that fixes it or be told who can.
**Status:** Planned

## Preconditions

- The console user is authenticated (UC-001) and has an active record in `survey.users`.
- That record has no entry in `survey.user_departments`. On a fresh deployment this is every
  account: no department is seeded (C-016), so the first administrator to sign in has none.

## Main Success Scenario

1. An administrator signs in to a fresh deployment.
2. The system finds no department on their account and shows a dialog that blocks every
   console screen and cannot be dismissed.
3. The dialog explains the condition and offers one action, Add a department, and Logout.
4. The administrator follows the action to Departments and creates a department (UC-006).
5. The system assigns the new department to the administrator in the same transaction as it
   saves the department.
6. On the next screen the administrator opens, the dialog is gone and the console is usable.

## Alternative Flows

### A1: The signed-in user is not an administrator

**Trigger:** The console user holds `elicit_user` only (step 2).
**Flow:**

1. The dialog explains that an administrator must assign them to a department, and offers
   Logout as its only action.
2. Use case ends; the user returns once an administrator has acted (UC-008).

### A2: The administrator navigates away without creating a department

**Trigger:** The administrator opens any screen other than Departments or Edit Department (step 4).
**Flow:**

1. The dialog appears again on that screen.
2. Use case continues at step 3.

### A3: Another administrator assigns the department

**Trigger:** While the dialog is shown, a different administrator edits the blocked account and
assigns it a department (UC-008), or the blocked administrator does so on their own record.
**Flow:**

1. On the next screen the blocked user opens, the system re-reads their record, finds the
   department, and shows no dialog.
2. Use case ends; no re-login was needed.

### A4: The principal has no console record

**Trigger:** The identity provider authenticated a principal with no active row in `survey.users` (step 2).
**Flow:**

1. The system shows no dialog; UC-001's handling of a missing record applies.
2. Use case ends.

## Postconditions

### Success Postconditions

- The signed-in user is assigned to at least one department.
- The dialog is gone on the next screen, with no restart and no re-login.
- A department created from this dialog is assigned to the administrator who created it.

### Failure Postconditions

- The account stays without a department and the console stays blocked; the user can still
  log out.

## Business Rules

### BR-110: This notice blocks, unlike every other console notice

A missing survey (UC-019, BR-077) or a seeded account (UC-021, BR-087) leaves the console
usable, so those notices explain and never block. A missing department leaves nothing usable:
Search lists nothing and Register cannot be completed. This notice is therefore modal, with no
close button, no Escape and no click outside.

### BR-111: The remedy path stays open

For an administrator, Departments and Edit Department are never blocked, because that is where
the remedy lives. A blocking notice that blocked its own remedy would leave a deployment
unrecoverable without direct database access.

### BR-112: Logout is always available

Every variant of the dialog offers Logout. A user who cannot fix the condition is never trapped
in a signed-in session they cannot end.

### BR-113: Creating a department assigns it to its creator

Saving a new department and assigning it to the administrator saving it happen in one
transaction, so an administrator can never end up having created a department they are not
assigned to. The rule is unconditional: an administrator who sets up several departments
belongs to all of them, and picks one explicitly when registering (UC-003).

### BR-114: The condition is re-established as the console is used

Whether the user has a department is checked on every screen, not captured once at sign-in.
When the session copy of the record shows none, the record is re-read from the database, so an
assignment made anywhere clears the dialog on the next screen (BR-076, BR-086).

### BR-115: A fresh install ships no department

The dev-data migration seeds the two accounts and the `email` message type only. It creates no
department, no message template (a template cannot exist without a department) and no
department assignments. The first department is created through the console.

---

## Reference

Traces to FR-035, NFR-016 and C-016. Follows the shape of UC-019 and UC-021 for where the check
runs (the layout, on every navigation), and departs from them in blocking (BR-110).

Implemented by `MissingDepartmentDialog` (the two variants and their actions),
`MainLayout.showRouterLayoutContent` (the gate and the remedy-screen exemption, BR-111),
`UiSessionLogin.refresh` (the re-read, BR-114), `DepartmentService.create` (the assignment,
BR-113), `EditDepartmentView` and `EditUserView` (which call the re-read after a save) and
`User.hasDepartments`. The seed change is in `V0.0.3__POPULATE_DEV_DATA.sql`; the overview items
come from `SetupWarnings`. Verified by `MissingDepartmentDialogTest`, `DepartmentServiceTest`,
`UiSessionLoginTest` and `EditDepartmentViewTest`.

A database that has already run the earlier `V0.0.3` keeps its seeded department: applied
migrations are never re-run, and Flyway's repair-at-start realigns the checksum. The dialog is
only ever seen on a database created after this change.
