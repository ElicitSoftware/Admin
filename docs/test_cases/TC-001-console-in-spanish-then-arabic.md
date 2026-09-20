# Test Case: Console in Spanish then Arabic

## Overview

**ID:** TC-001
**Goal:** An administrator whose browser prefers Spanish opens the console, switches it to Arabic, and works through subject search and registration with every console text in the chosen language and the screens mirrored right-to-left — verifying language negotiation, switching, layout direction and session persistence end-to-end.
**Priority:** High
**Status:** Draft

## Roles

- Survey Administrator (signs in, chooses the language, searches and registers subjects)

## Preconditions

- An administrator account exists with the `elicit_user` role (Keycloak realm import `keycloak/elicit-realm.json`; test seed `V0.0.3__POPULATE_DEV_DATA.sql`).
- A survey is installed (test seed) so subject search and registration screens have content.
- Spanish (`es-419`) and Arabic (`ar`) language files ship with the console (`src/main/resources/vaadin-i18n/`).
- The browser's preferred language is set to Spanish (Latin America).

## Flow

| Step | Name                       | Description                                                                                                       | Test Data                    | Use Case                                                          |
|------|----------------------------|-------------------------------------------------------------------------------------------------------------------|------------------------------|-------------------------------------------------------------------|
| 1    | Sign in                    | The administrator signs in through the identity provider and reaches the console                                  | admin / admin                | [UC-001](../use_cases/UC-001-authenticate-and-access-the-admin-console.md) |
| 2    | Verify Spanish console     | The navigation items, header title and page title are shown in Spanish and the screen is laid out left-to-right    | -                            | -                                                                 |
| 3    | Switch to Arabic           | The administrator chooses Arabic in the header's language selector                                                | ar                           | [UC-020](../use_cases/UC-020-use-the-console-in-my-language.md)   |
| 4    | Verify Arabic console      | The same texts are shown in Arabic, the screen direction is right-to-left and the document language is Arabic      | -                            | -                                                                 |
| 5    | Search subjects            | The administrator opens subject search and searches by last name                                                  | Tester                       | [UC-002](../use_cases/UC-002-search-and-monitor-subject-progress.md) |
| 6    | Verify Arabic grid         | Grid column headers and action buttons are Arabic while subject names appear as stored                            | -                            | -                                                                 |
| 7    | Register a subject         | The administrator registers a new subject                                                                         | Ana, Prueba, ana.prueba@example.org | [UC-003](../use_cases/UC-003-register-or-update-a-subject.md) |
| 8    | Verify language kept       | The confirmation and the registration screen are still Arabic and right-to-left without choosing again           | -                            | -                                                                 |

## Validation

1. **Session language persists**: After step 8, opening any other console screen in the same browser session shows Arabic; a new browser session falls back to the browser's Spanish preference.
2. **No untranslated text**: No console text on any visited screen is shown as an English fallback or a `!key!` marker.
3. **Data untouched**: The subject registered in step 7 is stored with the names as typed, regardless of the console language.

## Postconditions

- One subject "Ana Prueba" with email `ana.prueba@example.org` exists with a generated access code.
- No language information is stored with the administrator's user record; seeded data remains untouched.
