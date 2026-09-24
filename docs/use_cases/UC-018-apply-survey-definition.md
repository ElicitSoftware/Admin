# Use Case: Apply Survey Definition

## Overview

**Use Case ID:** UC-018
**Use Case Name:** Apply Survey Definition
**Primary Actor:** Survey Administrator
**Goal:** Install an authored survey definition into this deployment without the administrator having to determine first whether that survey already exists here, so one distributed file can be applied unchanged at every deployment in a multi-site rollout.
**Status:** Implemented

## Preconditions

- The administrator presents a valid bearer token and holds the `elicit_admin` role (UC-001).
- The file is a survey definition produced by UC-013 and carries a stable survey key.

## Main Success Scenario

1. The administrator uploads a survey definition file, without nominating a target survey.
2. The system reads the stable survey key from the file's survey record.
3. The system looks for a survey already installed in this deployment under that key.
4. If none exists, the system installs the file as a new survey (UC-014).
5. If one exists, the system applies the file to it in place (UC-017), including that use case's revision-regression check, which routing neither relaxes nor overrides.
6. The system reports which of the two it performed, along with that use case's own result.
7. The system asks the Survey application to rebuild its reporting schema (Survey UC-008, `POST /api/etl/build`), so the survey just installed or updated has its dimensions, fact columns and views without a restart of Survey, and adds the outcome to its report: "Reporting schema rebuilt." or "Reporting schema not rebuilt: reason".

## Alternative Flows

### A1: No file provided

**Trigger:** The request carries no file (step 1).
**Flow:**

1. The system rejects the request as a bad request.

### A2: File has no stable survey key

**Trigger:** The file predates stable-key assignment (step 2).
**Flow:**

1. The system rejects the file, since it cannot be matched against the surveys installed here.
2. The administrator may still install it explicitly as a new survey (UC-014) if that is the intent.

### A3: File is not a survey definition

**Trigger:** The file contains no survey record at all, or its survey record is malformed (step 2).
**Flow:**

1. The system rejects the file and attempts nothing.

### A4: Destination use case fails

**Trigger:** The selected use case (UC-014 or UC-017) rejects or fails on the file (steps 4–5).
**Flow:**

1. The system reports which use case was selected and that use case's own failure, unchanged. Routing adds no recovery of its own.
2. The reporting schema is not rebuilt: nothing was applied.

### A5: Reporting schema rebuild fails

**Trigger:** In step 7 the Survey application cannot be reached, does not answer within 60 seconds, answers that its reporting ETL is disabled (`elicit.etl.enabled=false`, HTTP 409), or reports that the build failed (HTTP 500 -- the known case being two surveys that share a step or section dimension name, which breaks Survey's site-wide `dim_step_un`/`dim_section_un` constraints).
**Flow:**

1. The system reports the apply as successful, with "Reporting schema not rebuilt:" and Survey's own reason as the last line of the result, and logs the reason at WARN.
2. The administrator fixes the cause (starts Survey, renames the colliding dimension, ...) and either re-applies the same file -- an equal revision is not a regression (UC-017 BR-070) and every record reconciles as unchanged -- or restarts Survey, whose startup build covers the same ground.

### A6: Reporting schema rebuild is switched off

**Trigger:** `elicit.survey.etl-build.enabled=false` (step 7).
**Flow:**

1. The system asks Survey nothing and adds no reporting line to the result.

## Postconditions

### Success Postconditions

- The survey exists in this deployment under the file's stable survey key, either newly installed or updated in place.
- The audit record written by the destination use case records which operation occurred and the revision applied.
- The Survey application has been asked to rebuild its reporting schema, and the result says whether it did.

### Failure Postconditions

- No changes are applied. Routing itself writes nothing; the destination use case's own all-or-nothing guarantee governs.

## Business Rules

### BR-073: The stable survey key decides create versus update

Whether a definition file is an installation or an update is a property of the receiving deployment, not of the file — the same file is a create at a deployment seeing the survey for the first time and an update at one that already has it. The system resolves this by looking up the file's stable survey key locally, so no per-deployment instruction has to accompany a distributed file.

### BR-074: Routing performs no work of its own

This use case only selects between UC-014 and UC-017 and returns the selected use case's result unchanged. It applies no records, relaxes none of that use case's validation, and adds no transaction of its own.

### BR-075: An unmatched file is refused, not guessed

A file with no stable survey key cannot be matched against what is installed. The system refuses it rather than defaulting to installing a new survey, because a duplicate survey created by a wrong guess is materially harder to undo than a rejected upload.

### BR-107: The reporting schema is rebuilt after every successful apply

Survey builds its reporting star schema from the survey definitions present when it starts and nothing else rebuilds it, so a survey applied afterwards has no dimensions until Survey restarts. After a successful install or update -- through this use case, or through UC-014 or UC-017 directly -- the system asks Survey to rebuild, once, after the apply's transaction has committed so Survey sees the new rows. The call goes to `elicit.survey.url` (default `http://survey:8080`) and can be switched off with `elicit.survey.etl-build.enabled=false` for a deployment that restarts Survey after every apply or runs it without reporting.

### BR-108: A failed rebuild does not undo the apply

The definition is committed before Survey is asked, and a reporting schema that is behind is recoverable (repeat the apply, or restart Survey) where a rolled-back apply is not. So the rebuild's outcome is reported -- on the result and in the log -- and never fails the apply, changes its title, or is written into the apply's audit record, which was committed with the apply itself.

---

## Reference

Implemented by `SurveyDefinitionApplyResource` / `SurveyDefinitionApplyService`, which delegate to `SurveyDefinitionImportService` (UC-014) and `SurveyDefinitionUpdateService` (UC-017), and by `ReportingSchemaRebuildClient` for step 7 (Survey's `ETLBuildResource`, its UC-008). The Connections screen (UC-025) lists the Survey application so its address can be checked before an apply. Verified by `SurveyDefinitionApplyServiceTest`, `SurveyDefinitionApplyViewTest`, `ReportingSchemaRebuildClientTest` and `SurveyDefinitionApplyReportingRebuildTest`. The explicit UC-013/UC-014/UC-017 endpoints remain available for an administrator who wants to assert which operation should occur and have the request fail if that assertion is wrong.
