# Use Case: Generate Survey Access Code

## Overview

**Use Case ID:** UC-015
**Use Case Name:** Generate Survey Access Code
**Primary Actor:** *(included subflow — no direct actor)*
**Goal:** Produce a unique, human-friendly survey access code and respondent record so a subject can access their survey.
**Status:** Implemented

## Preconditions

- A subject is being registered for a specific survey (invoked by UC-003 or UC-010).

## Main Success Scenario

1. The system generates a candidate access code from a character set that excludes visually ambiguous characters.
2. The system checks the candidate is not already used by another respondent for the same survey.
3. If the candidate is unique, the system creates a respondent carrying that access code for the survey.
4. The access code is returned to the calling registration flow for storage on the subject and substitution into its messages.

## Alternative Flows

### A1: Access code collision

**Trigger:** The candidate access code already exists for the survey (step 2).
**Flow:**

1. The system discards the candidate and generates another.
2. The main flow resumes at step 2.

### A2: Unable to generate a unique access code

**Trigger:** A unique access code cannot be produced.
**Flow:**

1. The system raises an access code generation error, which the calling use case surfaces to the caller (UC-003 A4, UC-010 A3).

## Postconditions

### Success Postconditions

- A respondent exists with a unique, survey-scoped access code.

### Failure Postconditions

- No respondent or access code is created; the caller is notified of the failure.

## Business Rules

### BR-050: Human-friendly access code alphabet

Access codes are drawn from a character set that omits visually ambiguous characters (such as `0`/`O` and `1`/`l`/`I`) to reduce transcription errors.

### BR-051: Access code uniqueness within a survey

An access code must be unique among respondents of the same survey.

---

## Reference

Derived from `AccessCodeService.generateAccessCode` and `Respondent`. Note: the retry counter in the generation loop increments rather than decrements, so the "try a limited number of times" intent is not enforced as written — flagged for review.
