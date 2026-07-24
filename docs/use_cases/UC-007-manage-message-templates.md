# Use Case: Manage Message Templates

## Overview

**Use Case ID:** UC-007
**Use Case Name:** Manage Message Templates
**Primary Actor:** Survey Administrator
**Goal:** Author and maintain reusable email/message templates, scoped to a department, that are sent to subjects.
**Status:** Implemented

## Preconditions

- The administrator is authenticated and holds the `elicit_admin` role (UC-001).
- The administrator is affiliated with at least one department.

## Main Success Scenario

1. The administrator opens the message templates view and sees existing templates with their department, subject line, and content type.
2. The administrator chooses to create a new template or edit an existing one.
3. The administrator selects the content type and department, and enters the subject line and body.
4. The system shows a live preview of the rendered body as the administrator edits.
5. The administrator saves.
6. The system validates the values and stores the template, then returns to the list.

## Alternative Flows

### A1: Validation errors

**Trigger:** The subject line or body is missing or exceeds its length limit, or the content type is not chosen (step 6).
**Flow:**

1. The system asks the administrator to correct the errors before saving.

## Postconditions

### Success Postconditions

- The template is created or updated and appears in the list.

### Failure Postconditions

- No change is stored, and the administrator is told why.

## Business Rules

### BR-026: Subject line and body required

A template requires a non-empty subject line (up to 255 characters) and a non-empty body (up to 6000 characters).

### BR-027: Content type restricted

A template's content type must be one of the supported values: HTML or plain text.

### BR-028: Department-scoped authoring

The department chosen for a template is limited to the departments the authoring administrator is affiliated with.

---

## Reference

Derived from `MessageTemplatesView`, `EditMessageTemplatesView`, `MessageTemplate`, `MessageType`, and `Department`.
