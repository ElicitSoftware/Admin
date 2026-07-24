# Use Case: Register Subjects via Integration API

## Overview

**Use Case ID:** UC-010
**Use Case Name:** Register Subjects via Integration API
**Primary Actor:** Integration Client
**Goal:** Register one or many subjects programmatically (single request, batch request, or CSV upload) so each receives an access token and invitation messages.
**Status:** Implemented

## Preconditions

- The client presents a valid bearer token from the OIDC provider.
- Single and batch registration require the `elicit_importer`, `elicit_admin`, or `elicit_user` role; CSV upload requires the `elicit_importer` role.
- The referenced survey and the target department (with its message templates) exist.

## Main Success Scenario

1. The client submits one or more subject records (as a single object, an array, or a CSV file).
2. For each record, the system confirms the external ID is not excluded for the department.
3. The system confirms no subject with that external ID already exists in the department.
4. The system generates a unique access token and creates a respondent (UC-015).
5. The system stores the subject and generates the department's default messages for it.
6. The system returns a per-record outcome: newly registered, already existing, or excluded.

## Alternative Flows

### A1: External ID excluded

**Trigger:** A record's external ID is on the department's exclusion list (step 2).
**Flow:**

1. The system records the outcome as "excluded" for that record and creates nothing.

### A2: Subject already exists

**Trigger:** A subject with the same external ID already exists in the department (step 3).
**Flow:**

1. The system records the outcome as "existing" for that record and creates nothing.

### A3: Per-record failure in a batch

**Trigger:** Token generation fails, or a record is otherwise invalid, during a batch or CSV import (step 4).
**Flow:**

1. The system records an error for that record only and continues processing the remaining records.

### A4: No CSV file provided

**Trigger:** A CSV upload request carries no file.
**Flow:**

1. The system rejects the request, reporting that no file was provided.

### A5: Malformed CSV rows

**Trigger:** One or more CSV rows are missing required fields or have an unparseable department ID or date.
**Flow:**

1. The system collects the line-level errors and reports that the import completed with errors, identifying the failing lines.

## Postconditions

### Success Postconditions

- Each successfully processed record has a subject, a respondent, an access token, and queued messages.
- The caller receives a per-record status list.

### Failure Postconditions

- Records that were excluded, duplicate, or invalid create nothing; their status explains the outcome.

## Business Rules

### BR-034: Role-tiered access to the API

Single-subject and batch registration accept the importer, admin, or user role; CSV import is restricted to the importer role.

### BR-035: CSV column order

A CSV row supplies fields in the order: department ID, first name, last name, middle name, date of birth, email, phone, external ID. Lines beginning with `#` and blank lines are ignored.

### BR-036: CSV required fields

Each CSV row requires a parseable department ID, first name, last name, and email; date of birth accepts `yyyy-MM-dd` or `MM/dd/yyyy`.

### BR-037: Independent per-record processing

In batch and CSV registration, each record is evaluated independently; one record's exclusion, duplication, or failure does not stop the others.

The exclusion (BR-013), duplicate (BR-012), required-field (BR-009), and message-creation (BR-014) rules from UC-003 apply identically here.

---

## Reference

Derived from `TokenService` (`/secured/add/subject`, `/add/subjects`, `/add/csv`), `CsvImportService`, `AddRequest`, `AddResponse`, `ExcludedXid`, `Subject`, `Respondent`, and `Message`. Note: the CSV path currently registers all rows against survey ID 1.
