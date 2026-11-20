# Use Case: Register or Update a Subject

## Overview

**Use Case ID:** UC-003
**Use Case Name:** Register or Update a Subject
**Primary Actor:** Survey User
**Goal:** Add a subject to a survey (individually or by CSV upload) so they receive an access code and invitation messages, or update an existing subject's details.
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
7. The system generates a unique survey access code and creates a respondent (UC-015).
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

### A4: Access code generation fails

**Trigger:** A unique access code cannot be generated (step 7).
**Flow:**

1. The system reports an access code generation error and asks the user to try again.
2. No subject is created.

### A5: Update an existing subject

**Trigger:** The user chooses Edit on a subject's row in the search view (UC-002). The edit screen is addressed by the subject's record id, never by the access code (NFR-022).
**Flow:**

1. The system loads the subject's current details and switches to update mode.
2. The user changes fields and saves.
3. The system validates and stores the changes, then returns to the search view.
4. The access code and messages are **not** regenerated.

### A6: Bulk upload by CSV

**Trigger:** The user uploads a CSV file of subjects.
**Flow:**

1. If any data row lacks the survey ID column, or leaves it empty, the system rejects the whole file with a warning naming those lines and imports nothing (BR-175). The survey chosen in the form plays no part in an upload.
2. The system reads every row without registering anyone and shows a confirmation: how many subjects it will register on each survey and in each department, and each row it will refuse, named by its line number and reason — a department the user is not assigned to (UC-010 BR-137), a department that does not have the row's survey (UC-006 BR-172), a survey that is not installed, or a value that fails validation (BR-176).
3. If the user cancels, nothing is imported.
4. If the user confirms, the system imports the rows it reported as accepted through the single-subject flow and shows a per-subject result summary (registered, already existing, or excluded), one line per row, with the refused rows listed by line number.
5. See UC-010 for the underlying registration and validation rules.
6. The upload is a console function: it runs under the user's own role and the departments of their console user record, and is not a use of the registration API or of the `elicit_importer` role (UC-010 BR-034).

### A7: Subject is not in the user's departments

**Trigger:** The subject addressed for editing belongs to a department the user is not assigned to (A5 step 1), or the department of the subject being saved or updated is not one of the user's (step 5, A5 step 3).
**Flow:**

1. On opening, the system answers exactly as for a subject that does not exist: the not-found notice and an empty form in registration mode.
2. On save or update, the system tells the user the subject belongs to a department they are not assigned to and changes nothing.

## Postconditions

### Success Postconditions

- A subject and its respondent exist, an access code is assigned, and invitation/reminder messages are queued.
- On update, the subject's details reflect the changes; access code and messages are unchanged.

### Failure Postconditions

- No subject is created or modified, and the user is told why (validation, exclusion, duplicate, or access code error).

## Business Rules

### BR-009: Required subject fields

A subject requires a department, first name, last name, and an email address in a valid format. The email address is the subject's one required contact: a phone number is optional (BR-010).

### BR-010: Phone format

If provided, phone must match the pattern ###-###-#### .

### BR-011: Date of birth in the past

If provided, date of birth must be a past date.

### BR-012: External ID unique within a department

An external ID may appear at most once per department.

### BR-013: Exclusion list blocks registration

A subject whose external ID is on the department's exclusion list cannot be registered.

### BR-014: Messages created only at registration

Invitation/reminder messages are generated when a subject is first registered. Updating a subject does not regenerate the access code or messages.

### BR-174: The survey choice follows the department

The form offers only the surveys of the department chosen in it (UC-006 BR-172), and refuses a save whose department does not have the survey chosen.

### BR-175: A CSV upload names its survey on every row

A CSV row carries the survey's ID as its first column, before the department ID; a row without phone may end at the email. A file in which any data row lacks the survey ID is rejected whole, with a warning, and nothing is imported.

### BR-176: A CSV upload is confirmed before anything is registered

On the Register page, an upload registers no one until the user has seen what it will register and refuse and has confirmed it.

### BR-138: A subject is edited only within the user's departments

A subject is loaded for editing only when its department is one of the user's. On save and on update the system refuses the change when the department chosen is not one of the user's, and on update also when the stored subject's department is not. The user's departments are read from the database at that moment, not from the session (NFR-021).

---

## Reference

Derived from `RegisterView`, `AccessCodeService`, `CsvImportService`, `Subject`, `ExcludedXid`, `Message`, and `Department`.
