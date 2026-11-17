# Use Case: Manage Departments

## Overview

**Use Case ID:** UC-006
**Use Case Name:** Manage Departments
**Primary Actor:** Survey Administrator
**Goal:** Maintain the organizational departments used to segregate subjects, decide which surveys their subjects may be registered onto, and identify outbound email.
**Status:** Implemented

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).

## Main Success Scenario

1. The administrator opens the departments view and sees the list of departments with name, code, default message ID, sender email, and surveys.
2. The administrator chooses to create a new department or edit an existing one.
3. The administrator enters or changes the department name, code, default message ID, sender email, and the surveys the department may register subjects onto (BR-172). A new department's surveys start as every installed survey (BR-173).
4. The administrator saves.
5. The system validates the values and stores the department, then returns to the list.

## Alternative Flows

### A1: Validation errors

**Trigger:** A required field is missing or a value fails its rule (step 5).
**Flow:**

1. The system asks the administrator to correct the errors before saving.

### A2: Duplicate name or code

**Trigger:** The name or code already belongs to another department (step 5).
**Flow:**

1. The system reports that the name or code already exists and does not save.

### A3: Department not found

**Trigger:** The administrator opens an edit link for a department that no longer exists, or an invalid identifier.
**Flow:**

1. The system reports the department was not found (or the identifier is invalid) and returns to the list.

## Postconditions

### Success Postconditions

- The department is created or updated and appears in the list.

### Failure Postconditions

- No change is stored, and the administrator is told why.

## Business Rules

### BR-022: Department name required and unique

A department name is required and must be unique across departments.

### BR-023: Department code unique

A department code, if provided, must be unique across departments.

### BR-024: Default message ID required

A department must have a default message ID, defaulting to the first template, identifying which message templates to send when a subject is registered.

### BR-025: Sender email required

A department must have a valid sender email address, used as the "from" address for its outbound mail.

### BR-172: A department's surveys bound registration

A department lists the surveys its subjects may be registered onto. A subject is registered onto a survey only when the subject's department lists it, whether by the Register form, a CSV upload (UC-003) or the registration API (UC-010). A department may list none, and then nobody can be registered in it.

### BR-173: Where a department's surveys start

A department created after this rule starts with every survey installed at that moment, which the administrator may narrow before saving. A survey applied later (UC-018) is in no department until an administrator adds it on this screen. The departments that existed when the rule was introduced were given every survey installed at that time.

---

## Reference

Derived from `DepartmentsView`, `EditDepartmentView`, `Department`, and the `survey.departments` and `survey.department_surveys` schema.
