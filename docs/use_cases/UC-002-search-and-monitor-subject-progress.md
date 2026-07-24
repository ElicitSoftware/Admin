# Use Case: Search and Monitor Subject Progress

## Overview

**Use Case ID:** UC-002
**Use Case Name:** Search and Monitor Subject Progress
**Primary Actor:** Survey User
**Goal:** Find subjects within the user's departments and monitor each subject's progress through their survey.
**Status:** Implemented

## Preconditions

- The user is authenticated and holds the `elicit_user` or `elicit_admin` role (UC-001).
- The user is affiliated with at least one department.

## Main Success Scenario

1. The user opens the console home (search) view.
2. The system loads the user's account and the departments they are affiliated with.
3. The system defaults the department filter to "All Departments" (scoped to the user's own departments) and displays a paginated, sortable list of subjects with their status (Not Started, In Progress, Finished).
4. The user optionally narrows the results by department, token, first name, last name, email, or phone and submits the search.
5. The system resets to the first page and displays the matching subjects.
6. The user pages through and sorts the results.
7. The system automatically refreshes the list periodically so progress stays current.

## Alternative Flows

### A1: No active user record

**Trigger:** No active application user exists for the signed-in identity (step 2).
**Flow:**

1. The system shows a message that the identity has no active application account and to contact an administrator.
2. No list is displayed.

### A2: No department selected

**Trigger:** The user clears the department filter entirely and searches (step 4).
**Flow:**

1. The system prompts the user to select one or more departments.
2. The result list is empty until a department is chosen.

### A3: Act on a subject

**Trigger:** The user selects a row action (step 6).
**Flow:**

1. The user chooses to edit the subject (UC-003), send an email (UC-004), or generate a report (UC-005).

## Postconditions

### Success Postconditions

- The user sees an up-to-date, department-scoped view of subject progress.

### Failure Postconditions

- No subject data is displayed (no account, or no department selected).

## Business Rules

### BR-005: Department-scoped visibility

A user may only search and view subjects within departments they are affiliated with. "All Departments" expands only to the user's own departments, never the whole system.

### BR-006: Progress status derivation

A subject's status is derived from respondent timestamps: "Not Started" when the survey has never been accessed and is not finalized, "In Progress" when accessed but not finalized, and "Finished" once finalized.

### BR-007: Single active row action

Only one subject row may have an action selected at a time; selecting an action on another row clears the previous selection.

### BR-008: Text filter matching

Token, first name, last name, and email filters match case-insensitively as partial matches; a blank filter is ignored.

---

## Reference

Derived from `SearchView`, `StatusDataSource`, the `survey.status` view (`V0.0.1__CREATE_ADMIN_SCHEMA.sql`), and `Status`.
