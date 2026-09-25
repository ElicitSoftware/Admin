# Use Case: Consult the Administrator's Manual

## Overview

**Use Case ID:** UC-029  
**Use Case Name:** Consult the Administrator's Manual  
**Primary Actor:** Survey Administrator  
**Goal:** Obtain a printable, illustrated manual that walks through running the console — from the first department to a downloaded report and a survey definition applied — stamped with the version of Admin it was shipped with, so that the procedure can be followed away from the screen and attributed to one build.  
**Status:** Draft  
**Requirements:** [FR-036, NFR-017, C-017](../requirements.md)

## Preconditions

- The reader is signed in with the `elicit_admin` or the `elicit_user` role.
- The running application was built from an image that carries the manual.

## Main Success Scenario

1. The administrator chooses Manual in the console.
2. The system opens the administrator's manual in a new browser tab as a printable document.
3. The system shows a title page naming the application, the version of the application the manual was built with, and the date that build was made.
4. The administrator reads the manual: an orientation chapter that names the pieces the console works with — department, user, role, subject, respondent, access code, message template and survey definition — then one chapter per piece of the work: signing in, creating the first department, adding users and granting them roles, writing message templates, registering subjects and issuing access codes, inviting and reminding them, searching and monitoring progress, generating and downloading a report, moving a respondent's data between deployments, applying and exporting a survey definition, and reading the System screens.
5. The system illustrates each procedure with a screenshot of the screen the step describes, captioned and numbered, so that the administrator can match the instruction to what is on screen.
6. The system marks each procedure that only an administrator may carry out, so that a reader holding the user role knows which screens their role does not reach.
7. The system repeats the version and the build date in the footer of every page.
8. The administrator follows the manual, on screen or on paper, and carries out the procedure it describes.

## Alternative Flows

### A1: The image carries no manual

**Trigger:** The application was built without the manual, so there is nothing to open (step 1)  
**Flow:**

1. The system leaves the Manual entry out of the navigation.
2. The administrator works from the System overview and the on-screen help texts instead.
3. Use case ends.

### A2: The administrator prints the manual

**Trigger:** The administrator prints or saves the opened document (step 4)  
**Flow:**

1. The administrator prints the manual from the browser or saves the file.
2. The printed copy carries the same version and build date on the title page and in every footer, so the paper copy stays attributable to the build it came from.
3. Use case continues at step 8.

### A3: The build recorded no version

**Trigger:** The manual is built outside the application build, so no version or build date was supplied (step 3)  
**Flow:**

1. The system builds the manual with the version and the date both shown as "unknown".
2. The manual is otherwise complete and readable.
3. Use case continues at step 4.

### A4: The reader holds no console role

**Trigger:** The signed-in reader holds neither `elicit_admin` nor `elicit_user` (step 1)  
**Flow:**

1. The system refuses access to the manual as it refuses the console's views.
2. Use case ends.

### A5: The reader holds only the user role

**Trigger:** A reader signed in with `elicit_user` reaches a chapter describing an administrator-only procedure (step 6)  
**Flow:**

1. The manual names the role the procedure requires and directs the reader to ask an administrator.
2. Use case continues at step 8.

### A6: The reader needs to install or provision the platform

**Trigger:** The administrator looks for how to install the database, the identity provider, or the Survey, Admin and Author services, or how to make a further language available to them (step 4)  
**Flow:**

1. The manual names the installation manual as the place that procedure lives — installing and wiring the services, and adding a language (umbrella UC-015, UC-016) — and does not repeat it.
2. Use case continues at step 8.

### A7: The console is blocked by the missing-department notice

**Trigger:** The reader's account is not assigned to a department, so the notice of UC-028 is holding the console (step 1)  
**Flow:**

1. The notice offers the manual alongside its own action, because the manual is what describes the procedure the notice is demanding.
2. The administrator opens the manual and reads the chapter on creating the first department.
3. Use case continues at step 8.

## Postconditions

### Success Postconditions

- Nothing in the console's data is changed; the reader holds a manual whose title page and page footers name the version and build date of the running application.

### Failure Postconditions

- No manual is shown; the console is otherwise unaffected and its on-screen help texts remain available.

## Business Rules

### BR-001: One vocabulary with the console

The manual uses the same names the console shows — department, user, role, subject, respondent, access code, message template, survey definition — and never introduces a term the console does not display. The credential a respondent enters is the **access code**, never a "token" (C-011).

### BR-002: Every procedure is shown as well as told

Each procedure carries at least one captioned screenshot of the screen it describes. A procedure described only in prose is incomplete.

### BR-003: The manual is stamped with its build

The version and the date recorded by the application build appear on the title page and in the footer of every page. When the build supplies neither, both read "unknown" rather than being omitted, so a printed copy is never silently undated.

### BR-004: The manual ships with the application

The manual is produced by the same build that produces the application image and travels inside it. The application never fetches the manual from an external location, and a manual that is absent is simply not offered (A1).

### BR-005: The manual is the default brand

The manual is typeset in the Elicit default brand — its logo, colours and typefaces — regardless of which brand a deployment mounts, so that one document serves every site.

### BR-006: One manual serves both roles

The manual is offered to `elicit_admin` and `elicit_user` alike. Rather than publishing two documents, it marks every administrator-only procedure where it appears, so that a user reading it is told what their role does not reach instead of finding a screen missing (A5).

### BR-007: The manual stops at the console's edge

The manual documents the console of an installed system, and it draws the line at what the reader
*does* rather than at which screen a thing sits on. Installing and wiring the platform belongs to the
umbrella installation manual, and so does making a language available — uploading a translation file
(umbrella UC-016) or mounting the translations directory (umbrella UC-015) provisions the deployment,
it is not a day's work in the console — while building a survey belongs to the author's manual. The manual names all three and refers
to them rather than repeating them (C-017, A6). Choosing a language for one's own session (UC-026) is a
reader's action and stays here.

### BR-008: The manual describes the build it ships in

A screen that is specified but not yet built is not described. When a planned capability reaches the console, its chapter is added in the same change, so that a reader never looks for a screen the running application does not have.

### BR-009: The manual is published in English

The console's chrome is translated (UC-026) and the entry that opens the manual is translated with it, but the manual itself is one English document. It describes the language selector rather than being reissued per language, so that a single build produces a single attributable manual.
