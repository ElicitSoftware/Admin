# Use Case: Register or Update a Subject

## Overview

**Use Case ID:** UC-003
**Use Case Name:** Register or Update a Subject
**Primary Actor:** Survey User
**Goal:** Add a subject to a survey (individually or by CSV upload) so they receive an access token and invitation messages, or update an existing subject's details.
**Status:** Implemented

## Preconditions

- The user is authenticated and holds the `elicit_user` or `elicit_admin` role (UC-001).
- The user is affiliated with at least one department.
- At least one survey and the target department's message templates exist.

## Main Success Scenario

1. The user opens the registration view.
2. The system presents a form with a department selector scoped to the user's departments (pre-selected if the user has exactly one).
3. The user enters the subject's first name, last name, and email, and optionally middle name, date of birth, phone, and external ID.
4. The user saves the form.
5. The system validates the entered values.
6. The system confirms the external ID is not on the department's exclusion list.
7. The system generates a unique survey access token and creates a respondent (UC-015).
8. The system stores the subject linked to that respondent and creates the department's default invitation/reminder messages for the subject.
9. The system confirms the subject was saved and clears the form for the next entry.

## Alternative Flows

### A1: Validation errors

**Trigger:** Required fields are missing or a value fails its format rule (step 5).
**Flow:**

1. The system highlights the invalid fields and asks the user to correct them.
2. No subject is created.

### A2: External ID is excluded

**Trigger:** The external ID is on the department's exclusion list (step 6).
**Flow:**

1. The system notifies the user that the external ID is excluded for the department.
2. No subject is created.

### A3: Duplicate external ID in department

**Trigger:** A subject with the same external ID already exists in the department (step 8).
**Flow:**

1. The system reports a duplicate entry and does not create a second subject.

### A4: Token generation fails

**Trigger:** A unique token cannot be generated (step 7).
**Flow:**

1. The system reports a token generation error and asks the user to try again.
2. No subject is created.

### A5: Update an existing subject

**Trigger:** The user opens the registration view for an existing subject's token.
**Flow:**

1. The system loads the subject's current details and switches to update mode.
2. The user changes fields and saves.
3. The system validates and stores the changes, then returns to the search view.
4. The token and messages are **not** regenerated.

### A6: Bulk upload by CSV

**Trigger:** The user uploads a CSV file of subjects.
**Flow:**

1. The system imports each row through the single-subject flow and shows a per-subject result summary (registered, already existing, or excluded).
2. See UC-010 for the underlying registration and validation rules.

## Postconditions

### Success Postconditions

- A subject and its respondent exist, an access token is assigned, and invitation/reminder messages are queued.
- On update, the subject's details reflect the changes; token and messages are unchanged.

### Failure Postconditions

- No subject is created or modified, and the user is told why (validation, exclusion, duplicate, or token error).

## Business Rules

### BR-009: Required subject fields

A subject requires a department, first name, last name, and a valid email address.

### BR-010: Phone format

If provided, phone must match the pattern ###-###-#### .

### BR-011: Date of birth in the past

If provided, date of birth must be a past date.

### BR-012: External ID unique within a department

An external ID may appear at most once per department.

### BR-013: Exclusion list blocks registration

A subject whose external ID is on the department's exclusion list cannot be registered.

### BR-014: Messages created only at registration

Invitation/reminder messages are generated when a subject is first registered. Updating a subject does not regenerate the token or messages.

---

## Reference

Derived from `RegisterView`, `TokenService`, `CsvImportService`, `Subject`, `ExcludedXid`, `Message`, and `Department`.
