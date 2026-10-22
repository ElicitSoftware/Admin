# Use Case: Monitor a Survey's Participation

## Overview

**Use Case ID:** UC-031
**Use Case Name:** Monitor a Survey's Participation
**Primary Actor:** Survey User, Survey Administrator
**Secondary Actor:** none
**Goal:** See how one survey is doing across the departments one belongs to — how many respondents were registered, started and finished, by department and by week, and how finished respondents met and answered the questions the survey's author marked for the administrator report — and take the same tables away as a CSV file or a PDF.
**Status:** Implemented

## Preconditions

- The user is signed in to the console with the `elicit_user` or `elicit_admin` role (UC-001) and belongs to at least one department (UC-028).
- At least one survey is installed (UC-014 or UC-018; UC-019 otherwise).

## Main Success Scenario

1. The user opens Participation from the console's navigation.
2. The system shows a survey picker (the first installed survey chosen), a department filter offering the user's own departments with "All departments" chosen, and an optional registration date range (BR-120).
3. The system computes, live from the survey tables (BR-127), the **participation** of the respondents the filter selects: registered, not started, in progress and finished, with the start, completion and overall rates (BR-121).
4. The system shows the same counts and rates **per department**, one row per department in the filter, and **per week**, one row for every week from the first registration to the last event in the population (BR-128).
5. The system shows one **card per element marked `ADMINISTRATOR`** (Survey UC-013), in the author's order, each listing the questions the mark covers (BR-123). For each question: how many times it was presented to finished respondents and to how many of them, how many times it was answered, the response rate and the reach (BR-124), and a distribution suited to its type (BR-125).
6. Every count from 1 to one below the threshold is shown as "< k", and every rate, share or statistic built on such a count is withheld (BR-122).
7. The user changes the survey, the departments or the dates; the system recomputes steps 3 to 6.
8. The user chooses **Download CSV** or **Download PDF**; the system produces the tables the page is showing, with the filter written at the top and the same suppression, and the browser saves the file (BR-126).

## Alternative Flows

### A1: The filter selects nobody

**Trigger:** No respondent matches the survey, departments and dates (step 3).
**Flow:**

1. The system shows zero participation, no rates (a rate with no denominator is shown as "—"), no weeks, and the cards with zero counts.
2. Use case continues at step 7.

### A2: The survey has no administrator marks

**Trigger:** The survey's `report_items` hold no `ADMINISTRATOR` row (step 5).
**Flow:**

1. In place of the cards the system says that the survey's author has marked no questions for this report, and that marks are made on Author's Reports page and arrive with the survey definition.
2. Use case continues at step 6.

### A3: The date range is reversed

**Trigger:** The "to" date is earlier than the "from" date (step 7).
**Flow:**

1. The date fields show that the range is empty, and nothing is recomputed until it is corrected.
2. Use case continues at step 7.

### A4: No survey is installed

**Trigger:** No survey is installed (step 2).
**Flow:**

1. The system shows the missing-survey notice (UC-019) instead of the page.
2. Use case ends.

## Postconditions

### Success Postconditions

- The user has seen, and optionally saved, aggregate counts of their departments' respondents for one survey. No record was changed.
- The opening of the page and each export are logged at INFO with the user, the survey's key and the filter, and no respondent data (BR-127).

### Failure Postconditions

- Nothing is shown or saved; no record is changed.

## Business Rules

### BR-120: The population is the user's departments' respondents of one survey

The page counts the `survey.respondents` rows of the chosen survey whose subject (`survey.subjects.respondent_id`) belongs to a department in the filter, and the filter can only hold departments the user belongs to, exactly as Search scopes its grid (UC-002). "All departments" means all of them. A respondent with no subject row — an auto-registered one (Admin #96) — is not counted, as Search does not show it. The optional registration date range keeps respondents whose `created_dt` falls on or after the "from" day and on or before the "to" day, in the server's time zone.

### BR-121: Participation states and rates follow the subject status

The states are those of the `survey.status` view the Search grid shows: **Not started** (no first access and not finalized), **In progress** (first access, not finalized) and **Finished** (finalized). **Started** is in progress plus finished. The start rate is started ÷ registered, the completion rate is finished ÷ started, and the overall rate is finished ÷ registered, each shown as a whole percentage; a rate whose denominator is zero is shown as "—".

### BR-122: Small counts are suppressed

The threshold *k* is the setting `elicit.participation.small-cell` (default 5; 1 or less turns suppression off). Every count that is greater than zero and less than *k* is shown as "< k" — in the participation table, the department and week tables, every card and both exports. A zero is shown, because it singles out nobody. A rate or share whose numerator or denominator is suppressed is withheld, and so is a number or date summary (minimum, quartiles, maximum, earliest, latest) computed from fewer than *k* answers. Suppression is per cell: a cell is not also hidden because a total and the other cells would let it be worked out, so a narrow department or week can still be inferred from its neighbours and the total; complementary suppression is left to a later version.

### BR-123: What a mark covers

The cards are the survey's `survey.report_items` rows of type `ADMINISTRATOR`, in `display_order` and then id order; a mark's label, when it has one, titles its card, and otherwise the question's or section's own text does. Each mark names a durable element (Survey UC-013), so it covers answers to every version of that element:
- a **question** mark covers the question wherever it is placed, and its card has one row;
- a **question placement** mark covers the question at that placement only, and its card has one row;
- a **section placement** mark covers every question placed in that section at that step and section position, one row per question in the section's question order.

Questions that hold no answer (`HTML`, `MODAL`) are left out. Texts are the survey's base-language text, as entered (C-014).

### BR-124: Presented and answered are counted among finished respondents

Per question, among the finished respondents of the population (umbrella plan D-5): **Presented** is the number of non-deleted answer rows, so a question in a repeating section counts once per instance (D-4), and **Respondents** is the number of distinct respondents those rows belong to. **Answered** is the number of those rows with a non-blank value; a `CHECKBOX` row counts as answered whatever its value, because a presented checkbox with no value is the answer No (Survey UC-012 BR-003). A default the respondent accepted is an answer, as it is to every other reader (Survey UC-012 BR-002). The **response rate** is answered ÷ presented, and the **reach** is respondents ÷ finished: the first says whether people answer the question, the second whether the survey's rules show it to them at all.

### BR-125: The distribution suits the question's type

- **Choice** (`RADIO`, `COMBOBOX`, `MULTI_SELECT`, `CHECKBOX_GROUP`, `MULTI_SELECT_COMBOBOX`): one row per option of the question's current option list, in option order, with the number of answers that chose it and its share of the answered count; a multiple-choice question's shares can add up to more than 100%. A stored value that no current option carries is listed after the options under the value itself.
- **Checkbox**: Yes and No.
- **Number** (`INTEGER`, `DOUBLE`): the count, minimum, first quartile, median, third quartile and maximum, the quartiles interpolated linearly. A value that is not a number is not counted.
- **Date** (`DATE_PICKER`): the earliest and latest date and the count per month.
- **Anything else**, free text above all: the count only. The page never shows what a respondent typed.

### BR-126: The exports are the page

The CSV file holds one block per table — the filter, the participation, the departments, the weeks and one block per question — separated by a blank line, each with its own header row, in UTF-8 with a byte order mark so that a spreadsheet opens it in the right encoding. The PDF holds the same tables, produced by the console's PDF service. Both carry exactly the suppressed values the page shows, in the console's language, and are named `participation-<survey name>-<yyyyMMdd>`. Neither holds a respondent's record: row-level data stays the respondent export (UC-011). The PDF's built-in font draws Latin script only, so when the console's language needs another script (Arabic) the PDF's own headings fall back to English, and any survey text it cannot draw is printed with "?" in place of the character.

### BR-127: Live, aggregate and logged

The figures are computed when the page is opened or the filter changes, from `survey.respondents`, `survey.subjects`, `survey.answers` and `survey.report_items`; nothing is precomputed. The page shows aggregates only and offers no way from a count to the respondents behind it (umbrella plan D-9). Opening the page and each export log the user, the survey's key and the filter at INFO.

### BR-128: Weeks start on Monday

A week is an ISO week, Monday to Sunday, in the server's time zone, labelled by its Monday. Per week the table counts the respondents of the population registered (`created_dt`), started (`first_access_dt`) and finished (`finalized_dt`) in that week, so a respondent registered in one week and finished in another appears in both. Every week from the first registration to the last event is listed, including weeks with nothing.

---

## Reference

Traces to FR-042, FR-043, NFR-018 and NFR-019; phase 5 of the umbrella's default-reports plan (`Elicit/docs/research/default_reports.md`, sections 4.1, 4.2, 4.4 and 4.5). Implemented by `ParticipationView` (the page, its filter and its two downloads), `ParticipationService` (the queries, BR-120 to BR-125 and BR-128), `ParticipationTables` (the one rendering of the figures into tables that the page and both exports share), `ParticipationExport` (the CSV and the PDF, BR-126) and `SmallCell` (BR-122); `PDFService` gains an overload whose page header names the survey. Migration `V0.0.21` grants `survey.report_items` to the console's database user. Verified by `ParticipationServiceTest`, `SmallCellTest`, `ParticipationExportTest` and `ParticipationViewTest`. The administrator's manual (UC-029) describes the page in "Monitoring a survey's participation".
