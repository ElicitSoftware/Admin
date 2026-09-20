# Translation request: Elicit Admin

This document is generated from the application's English text file and is meant to be handed,
as is, to a translator or to an AI translation agent. It contains everything needed to produce a
complete language file for one target language. The application itself ships only the English
file; a finished translation is placed in the deployment's translations directory
(`elicit-i18n/admin/` in the Elicit umbrella repository, mounted at `/opt/i18n`), never inside
the application.

## About the application

Elicit Admin is the administration console of the Elicit survey platform. Survey administrators
and clinical or research staff use it to register subjects, generate and send survey invitation
access codes, track each subject's progress, download reports, manage departments, message
templates and user accounts, and install survey definitions. The texts below are the console's
own words: navigation items, buttons, grid column headers, form labels, dialogs, notifications
and short help texts. Data entered by staff (names, departments, template bodies) and the survey
definitions themselves are not included and stay as entered.

**Audience and tone:** professional staff in a healthcare or research setting. Use clear,
concise, gender-neutral language and the formal register where the language distinguishes one
(for example *usted* in Spanish). Keep button labels short.

## Glossary and words to keep

| Term | Meaning | Rule |
|------|---------|------|
| Elicit | Product name | Never translate or transliterate |
| access code | The credential a subject types to open their survey | Translate consistently; never call it a "token" or "password" |
| subject / respondent | The person invited to take a survey (subject before, respondent once they answer) | Keep the distinction |
| department | Organizational unit that owns subjects and templates | Translate consistently |
| message template | Stored invitation or reminder email text | Translate consistently |
| survey definition | The authored survey installed into this console (the `.elicit` file) | Translate consistently; "apply" installs or updates it |
| role, administrator, user | Access levels | Translate consistently |
| PDF, CSV, JSON, HTTP, OIDC, API, ID, URL | Technical names | Keep as written |

## Rules for the translation

1. Keep every placeholder such as `{0}`, `{1}` exactly as written; move it inside the sentence
   where the language needs it, but never remove, rename or reorder it with another placeholder.
2. Where a row is flagged **params**, the value is processed by Java MessageFormat: any apostrophe
   in your translation must be doubled (`l''accès`). Rows without the flag may use a single
   apostrophe normally.
3. Where a row is flagged **html**, keep the HTML tags and translate only the text between them.
4. Respect the *Max length* column where given; these strings sit in buttons, menus and grid headers.
5. Do not translate the keys (the first column). Do not add, remove or reorder keys.
6. For right-to-left languages, write the text naturally; the console mirrors the layout.
7. Return exactly one file named `translations_<tag>.properties` (for example
   `translations_es_419.properties`, `translations_ar.properties`), UTF-8 encoded, with the same
   keys in the same order as the English file at the end of this document, one `key=translation`
   per line, and a first line `# Reviewed by <name or agent>, <date>`.

## Brand strings

Deployments mount their own brand. The organization name shown in the header comes from the
brand's own files and is translated there, not in this file, through a `localized` block keyed
by language tag in `brand-config.json` (`name`, `organization`) and `brand-info.json`
(`description`):

```json
{
  "name": "Healthcare Test Brand",
  "organization": "Healthcare Test Organization",
  "localized": {
    "es-419": { "name": "Marca de prueba de salud", "organization": "Organización de prueba de salud" },
    "ar": { "name": "علامة الرعاية الصحية التجريبية", "organization": "مؤسسة الرعاية الصحية التجريبية" }
  }
}
```

The base `name` also derives a technical identifier and must stay as it is.
