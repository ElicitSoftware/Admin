# Use Case: Use the Console in My Language

## Overview

**Use Case ID:** UC-026
**Use Case Name:** Use the Console in My Language
**Primary Actor:** Survey Administrator
**Goal:** See every screen of the admin console — labels, buttons, grid headers, messages, page titles and the organization name — in a language they read, laid out right-to-left when that language requires it, so that they can do their work without understanding English.
**Status:** Implemented

## Preconditions

- The administrator is authenticated and has reached the admin console (UC-001).
- The console ships English texts only; the deployment's translations directory supplies every other language (Latin American Spanish and Arabic in the reference deployment).
- The deployment may have mounted further language files or text overrides; if so, those languages are also available.

## Main Success Scenario

1. The administrator opens the console.
2. The system determines the language to use: the language chosen earlier in this browser session, otherwise the browser's preferred language, otherwise English.
3. The system displays the screen with every console text in that language, and with the screen laid out right-to-left when the language is written right-to-left.
4. The administrator chooses a different language from the language selector shown in the header of every screen.
5. The system redisplays the current screen in the chosen language, applies the matching layout direction, and remembers the choice for the rest of the browser session.
6. The administrator moves between screens — searching subjects, registering subjects, managing departments, templates and users, applying survey definitions.
7. The system presents every screen in the chosen language while stored data (subject names, department names, template texts, survey names) appears as entered, and the administrator completes their work in their language.

## Alternative Flows

### A1: A text is missing in the chosen language

**Trigger:** A language file lacks the translation for one of the texts on the screen (step 3).
**Flow:**

1. The system shows the English text for that item and the chosen language for everything else.
2. Use case continues at step 4.

### A2: Deployment mounted an additional language

**Trigger:** The platform operator has mounted a language file the console does not ship (step 4).
**Flow:**

1. The system lists the mounted language in the language selector alongside the shipped ones.
2. The administrator chooses the mounted language.
3. Use case continues at step 5.

### A3: Deployment overrides individual texts

**Trigger:** The platform operator has mounted a language file containing only some texts for a shipped language (step 3).
**Flow:**

1. The system shows the mounted text for the overridden items and the shipped text for all others.
2. Use case continues at step 4.

### A4: The sign-in screen is shown by the identity provider

**Trigger:** The administrator is not yet authenticated when opening the console (step 1).
**Flow:**

1. The identity provider presents its own sign-in screen in its own language.
2. After sign-in, use case continues at step 2.

## Postconditions

### Success Postconditions

- The chosen language is remembered for the browser session and applied to every screen until the administrator logs out or closes the browser.
- Stored data is unaffected by the language choice; only the console's own texts and layout direction differ.

### Failure Postconditions

- If no language can be determined, the console is shown in English; the administrator can still do everything.

## Business Rules

### BR-082: English is the fallback language

English is the default language and the fallback for any text missing from another language file. A text missing from every language file is shown as a visible marker (`!key!`) rather than blank, so the omission is noticed and fixed.

### BR-083: Available languages are shipped plus mounted

The languages offered are the union of the languages shipped with the console and the language files present in the deployment's mounted translations directory. A mounted file for a shipped language overrides only the texts it contains.

### BR-084: Language precedence

Session choice, then browser preference, then English. A language that is not available is skipped, never partially applied.

### BR-085: Layout direction follows the language

Languages written right-to-left (Arabic, Hebrew, Persian, Urdu and similar) mirror the whole screen layout; all other languages are laid out left-to-right. A deployment may declare the direction of a mounted language explicitly.

### BR-086: Stored data keeps its entered language

Subject, department, user and template records, survey names and survey definitions appear as entered, whatever language the console chrome uses. Email messages sent to subjects use the template text as stored.

### BR-087: Brand names are localized by the brand

The organization name and description supplied by the mounted brand may carry per-language variants; the console shows the variant for the current language and falls back to the brand's base text. The brand's technical identifiers are never translated.

### BR-088: The language choice never crosses sessions

The choice is held for the browser session only; nothing about the administrator's language is stored with their user record.
