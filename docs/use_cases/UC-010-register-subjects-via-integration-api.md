# Use Case: Register Subjects via Integration API

## Overview

**Use Case ID:** UC-010
**Use Case Name:** Register Subjects via Integration API
**Primary Actor:** Integration Client
**Goal:** Register one or many subjects programmatically (single request, batch request, or CSV upload) so each receives an access code and invitation messages.
**Status:** Implemented

## Preconditions

- The client presents a valid bearer token from the OIDC provider.
- The token carries the `elicit_importer` role and neither `elicit_admin` nor `elicit_user` (BR-034, BR-170).
- The referenced survey and the target department (with its message templates) exist.
- The client's principal has an active import client record (UC-032), assigned to every department it registers into (BR-137). The record carries the client's principal name; for a Keycloak service account that is `service-account-<client id>`.

## Main Success Scenario

1. The client submits one or more subject records (as a single object, an array, or a CSV file).
2. For each record, the system confirms the record's department has the record's survey (BR-177) and the external ID is not excluded for the department.
3. The system confirms no subject with that external ID already exists in the department.
4. The system generates a unique access code and creates a respondent (UC-015).
5. The system stores the subject and generates the department's default messages for it.
6. The system returns a per-record outcome: newly registered (`New Subject`), already existing (`Existing Subject`), or excluded (`Excluded Subject`).

## Alternative Flows

### A1: External ID excluded

**Trigger:** A record's external ID is on the department's exclusion list (step 2).
**Flow:**

1. The system records the outcome as "excluded" for that record and creates nothing.

### A2: Subject already exists

**Trigger:** A subject with the same external ID already exists in the department (step 3).
**Flow:**

1. The system records the outcome as "existing" for that record and creates nothing. The answer for that record carries the external ID and the department and nothing else of the stored subject (BR-164).

### A3: Per-record failure in a batch

**Trigger:** Access code generation fails, or a record is otherwise invalid, during a batch or CSV import (step 4).
**Flow:**

1. The system records an error for that record only and continues processing the remaining records. A record that breaks a subject rule is refused naming the field at fault (BR-188); an unexpected failure is reported as a fixed phrase with a reference (BR-139).

### A4: No CSV file provided

**Trigger:** A CSV upload request carries no file.
**Flow:**

1. The system rejects the request, reporting that no file was provided.

### A5: Malformed CSV rows

**Trigger:** One or more CSV rows are missing required fields, have an unparseable department ID or date, or break a subject rule (BR-188).
**Flow:**

1. The system collects the line-level errors and reports that the import completed with errors, identifying the failing lines.

### A6: Department not permitted

**Trigger:** A record names a department the caller's import client record is not assigned to (before step 2).
**Flow:**

1. For a single request, the system refuses the request as forbidden and stores nothing.
2. In a batch, the system records the outcome as "not permitted" for that record; in a CSV import it reports the line. The remaining records are processed (BR-037).

### A7: The caller is not an import client

**Trigger:** The token carries `elicit_importer` but its principal has no active import client record, or the token also carries `elicit_admin` or `elicit_user`; or the token does not carry `elicit_importer` at all (before step 1).
**Flow:**

1. The system refuses the whole request as forbidden, whichever of the three forms it took, reads no record and stores nothing (BR-034, BR-170).

### A8: The department does not have the survey

**Trigger:** A record names a survey its department does not list (UC-006 BR-172), or a survey that is not installed (step 2).
**Flow:**

1. For a single request, the system refuses the request as forbidden and stores nothing.
2. In a batch, the system records the outcome as "not permitted" for that record; in a CSV import it reports the line. The remaining records are processed (BR-037).

### A9: The CSV names no survey

**Trigger:** A CSV upload in which a data row lacks the survey ID column (BR-178).
**Flow:**

1. The system rejects the whole upload, naming the lines without a survey ID, and imports nothing.

## Postconditions

### Success Postconditions

- Each successfully processed record has a subject, a respondent, an access code, and queued messages.
- The caller receives a per-record status list.

### Failure Postconditions

- Records that were excluded, duplicate, or invalid create nothing; their status explains the outcome.

## Business Rules

### BR-034: The API is for import clients only

Single-subject registration, batch registration and CSV import accept the `elicit_importer` role and no other. A person registers subjects on the Register page (UC-003), whose CSV upload is a console function under the user's own role and department assignments and not a use of this API.

### BR-035: CSV column order

A CSV row supplies fields in the order: survey ID, department ID, external ID, first name, last name, middle name, date of birth, email, phone. Lines beginning with `#` and blank lines are ignored.

### BR-177: A record's department must have its survey

A record is registered only onto a survey its department lists (UC-006 BR-172). A record naming another survey, or one that is not installed, is refused as not permitted, by the same rule for single, batch and CSV registration.

### BR-178: A CSV names its survey on every row

Every data row of a CSV upload carries its survey ID as the first column. An upload in which any row lacks it is rejected whole and nothing is imported; the API does not fall back to any survey of its own choosing.

### BR-036: CSV required fields

Each CSV row requires a parseable survey ID and department ID, first name, last name, and email; date of birth accepts `yyyy-MM-dd` or `MM/dd/yyyy`.

### BR-188: A record must satisfy the subject rules

A record is registered only if the subject it describes satisfies the rules of UC-003: a first and last name (BR-009), an email address in a valid format (BR-009), a phone number, when given, as `###-###-####` (BR-010), a date of birth, when given, in the past, and no field longer than the subject stores (50 characters for a name, 255 for the email address, UC-003 BR-189). The record is checked before an access code is generated, so a refused record creates nothing: no subject, respondent, access code or message. The refusal names each field at fault and why, in the Register page's words (a length by its limit), and repeats nothing but the external ID the caller sent. It is the same for a single subject, a batch and a CSV import, the Register page's upload included (UC-003 A6); in a batch or an import, the other records go on (BR-037).

### BR-037: Independent per-record processing

In batch and CSV registration, each record is evaluated independently; one record's exclusion, duplication, or failure does not stop the others.

### BR-137: The caller's departments bound the API

The department rule reads from two places. A machine client registers where its import client record (UC-032) is assigned, a person, on the Register page, where their console user record (UC-008) is assigned, and a caller with neither registers nowhere. A record is processed only if its department is one of those; a record that is inactive or has no department permits none. A role from the identity provider says that a client may call the API, and the import client record says for which departments. The departments are read from the database for each request, and the check is made before the exclusion and duplicate checks, so a refused record tells the caller nothing about the department's subjects.

### BR-170: An import client is its record and the one role

A caller is an import client when its token carries `elicit_importer`, carries neither `elicit_admin` nor `elicit_user`, and its principal has an active import client record. A token with `elicit_importer` for a principal that has no such record, or whose record is inactive, is refused, and so is one that carries a console role beside it. The refusal is of the whole request, before any record is read, so it is the same for a single subject, a batch and a CSV import.

### BR-164: The answer does not disclose a stored subject

For a subject that already exists, the answer carries the outcome, the external ID and the department, and no other stored field: no name, date of birth, email, phone, status or access code. The access code is returned only for a subject this call created, together with what the caller itself sent. The same holds for a row of a CSV upload on the Register page.

### BR-139: An unexpected failure is reported as a fixed phrase

When a record, or a CSV import as a whole, fails for a reason the caller cannot correct (a persistence or other internal failure), the answer says so in fixed words followed by a reference — `Error (ref <id>)` — and nothing of the failure itself. The cause is written to the application log under the same reference (NFR-023). Outcomes the caller can act on (excluded, existing, not permitted, a CSV line's missing or malformed field) keep their own wording.

The exclusion (BR-013), duplicate (BR-012), required-field (BR-009), and message-creation (BR-014) rules from UC-003 apply identically here.

---

## Reference

Derived from `AccessCodeService` (`/secured/add/subject`, `/add/subjects`, `/add/csv`), `DepartmentScope`, `ImportClient`, `CsvImportService`, `AddRequest`, `AddResponse`, `ExcludedXid`, `Subject`, `Respondent`, and `Message`. Note: the CSV path currently registers all rows against survey ID 1.
