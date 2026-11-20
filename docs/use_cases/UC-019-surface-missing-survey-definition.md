# Use Case: Surface Missing Survey Definition

## Overview

**Use Case ID:** UC-019
**Use Case Name:** Surface Missing Survey Definition
**Primary Actor:** Survey Administrator
**Goal:** Learn from the console itself that this deployment has no survey installed, and where to install one, so that empty subject and reporting screens are explained rather than left to be inferred.
**Status:** Tested

## Preconditions

- The administrator is authenticated and has reached the admin console (UC-001).

## Main Success Scenario

1. The administrator opens the admin console.
2. The system checks whether this deployment has any survey installed.
3. Finding none, the system shows a warning on every console screen, giving the missing survey definition as the reason the console has nothing to work with.
4. The administrator opens a screen whose content depends on an installed survey, such as subject search or subject registration.
5. In place of that screen's empty result area, the system explains that no survey is installed and offers a way to reach Apply Survey Definition.
6. The administrator follows that offer and installs a survey definition (UC-018).
7. The system stops showing the warning and the explanations, and the screens present their normal content.

## Alternative Flows

### A1: A survey is already installed

**Trigger:** At least one survey exists in this deployment (step 2).
**Flow:**

1. The system shows neither the warning nor the per-screen explanations.
2. Use case ends.

### A2: Administrator continues without installing a definition

**Trigger:** The administrator navigates elsewhere instead of installing a definition (step 6).
**Flow:**

1. The system keeps the warning and the per-screen explanations in place.
2. The administrator may resume at any time from any screen.
3. Use case ends.

### A3: Administrator may not install survey definitions

**Trigger:** The administrator does not hold the role that Apply Survey Definition requires (step 5).
**Flow:**

1. The system states that no survey is installed and omits the offer to install one.
2. Use case ends.

### A4: The last survey is removed while the console is open

**Trigger:** Every survey is removed from the deployment after the console has begun showing its normal content (step 7).
**Flow:**

1. The system shows the warning again on the next screen the administrator opens.
2. Use case continues at step 4.

## Postconditions

### Success Postconditions

- While no survey is installed, every console screen carries the warning, and each screen that depends on a survey explains its own empty state.
- Once a survey is installed, neither the warning nor the explanations appear.
- This use case changes no stored data; it only reports what is installed.

### Failure Postconditions

- The console presents no indication that a survey is missing, leaving the administrator to infer it from empty screens.
- No stored data is changed, and no screen is blocked.

## Business Rules

### BR-076: The warning reflects what is installed now

Whether a survey is installed is re-established as the console is used, not captured once when the service starts. A deployment that starts empty and has a definition applied minutes later must stop warning without a restart, and one whose last survey is removed must start warning without one.

### BR-077: The warning explains, it never blocks

Every screen stays reachable and usable while the warning is shown. An administrator may still manage departments, templates and users in a deployment that has no survey, so the warning informs the administrator without withdrawing any function.

### BR-078: The remedy is offered only to those who can carry it out

Applying a survey definition is restricted (UC-018). An administrator without that role is told that no survey is installed but is not offered a route they cannot take, since an offer that fails on arrival is worse than none.

---

## Reference

Traces to FR-019. The missing-survey condition is already reported at service start
by `SurveyDefinitionPresenceCheck`, which logs a warning when `survey.surveys` is
empty; that covers the operator reading container logs but not the administrator
reading the console, which is what this use case adds. The remedy it points to is
UC-018, reached through the Apply Survey Definition screen.

Implemented by `SurveyDefinitionPresenceCheck.isSurveyInstalled()` (step 2, BR-076),
`MissingSurveyNotice` (the wording, and BR-078's choice of link or plain text),
`MainLayout.showRouterLayoutContent` (the banner, step 3) and the
`refreshMissingSurveyNotice()` hooks on `SearchView` and `RegisterView` (the per-view
explanations, step 5). Verified by `MissingSurveyWarningTest` and
`SurveyDefinitionPresenceCheckTest`.
