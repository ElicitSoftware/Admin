# Plan: the administrator's manual (UC-029)

The plan for [UC-029 — Consult the Administrator's Manual](../use_cases/UC-029-consult-the-administrators-manual.md),
satisfying FR-036, NFR-017 and C-017 in [`../requirements.md`](../requirements.md).

`Running the Console` — a printable, illustrated manual that ships inside the Admin image and is
opened from the console's navigation, exactly as Author ships `Building a Survey` (Author UC-039).
Author's implementation is proven and is the pattern this follows; the differences are called out
where they exist.

## Scope

**In:** the console as an installed system uses it — signing in, the first department, users and
roles, message templates, registering subjects and issuing access codes, invitations and reminders,
searching and monitoring progress, reports, moving a respondent between deployments, applying and
exporting a survey definition, and the System screens.

**Out (C-017, UC-029 BR-007, A6):** provisioning the deployment. The umbrella installation manual
covers bringing up the database, the identity provider, and Survey, Admin and Author — and it also
covers **making a language available**. Adding, replacing or removing a language (UC-027 for the
console and Survey, Author UC-041 for Author) provisions the deployment; it is not a day's work in
the console, so it belongs there and not here, wherever the screen that performs it happens to sit.
Building a survey stays in the author's manual. This manual names all three and refers to them.

**In, by the same test:** choosing a language for your own session from the selector (UC-026). That
is a reader's action, so it stays — one short chapter, no upload, no directory.

**Out (UC-029 BR-008):** anything not in the build.

## What gets added

| Path | Committed? | What it is |
| --- | --- | --- |
| `docs/manual/elicit-admin-manual.tex` | yes | the manual |
| `docs/manual/elicit-brand.sty` | yes | copied verbatim from Author — colours, IBM Plex, page furniture, `\screenshot`, `\ui`, `\term` (BR-005) |
| `docs/manual/images/` | yes | the captured screens, plus `elicit-logo.png` |
| `docs/manual/capture/` | yes | `capture-screenshots.mjs` + its own `package.json`, kept out of the Vaadin-managed root one |
| `docs/manual/build-manual.sh` | yes | typesets the PDF into `src/main/resources/manual/` |
| `docs/manual/README.md` | yes | how to build it and how to regenerate the figures |
| `docs/manual/build-info.tex` | **no** | generated: the version and build date of this build |
| `docs/manual/build/` | **no** | generated: LaTeX's working directory and the PDF |
| `src/main/resources/manual/` | **no** | generated: where the image picks the PDF up |
| `src/test/resources/manual/elicit-admin-manual.pdf` | yes | a small fixture so the tests do not need a LaTeX run |

`elicit-brand.sty` is duplicated rather than shared: Admin and Author are independent repositories
with no common build, and a copied style file is cheaper than a shared artifact. It is the one
file to re-copy when the brand changes.

## Chapters and figures

BR-002 requires a captioned screenshot per procedure. Every figure below is one screen of the
running console; the numbering is the capture order.

| # | Chapter | Figures | Covers |
| --- | --- | --- | --- |
| 1 | What the console manages | `01-navigation` | department, user, role, subject, respondent, access code, message template, survey definition. Access code, never "token" (C-011, BR-001) |
| 2 | Signing in | `02-sign-in`, `03-department-required` | the identity provider; the blocking notice of UC-028 and how it is cleared |
| 3 | Departments | `04-departments`, `05-edit-department` | UC-006; the first department is assigned to its creator (C-016) |
| 4 | Users and roles | `06-users`, `07-edit-user` | UC-008, UC-016; `elicit_admin` vs `elicit_user`, department assignment |
| 5 | Message templates | `08-message-templates`, `09-edit-message-template` | UC-007; the `<ACCESS_CODE>` placeholder |
| 6 | Registering subjects | `10-register-subject`, `11-register-result` | UC-003 and the access code it issues (UC-015) |
| 7 | Inviting and reminding | `12-search-email-action` | UC-004 |
| 8 | Finding subjects and watching progress | `13-search-filters`, `14-search-results` | UC-002 |
| 9 | Reports | `15-report-download` | UC-005 |
| 10 | Moving a respondent between deployments | `16-respondent-export`, `17-respondent-import` | UC-011, UC-012 |
| 11 | Installing and updating a survey | `18-apply-survey-definition`, `19-apply-result`, `20-export-survey-definition`, `21-missing-survey-notice` | UC-018 (with UC-014/UC-017 inside it), the reporting rebuild (FR-027), UC-013, UC-019 |
| 12 | The System screens | `22-system-overview`, `23-system-database`, `24-system-branding`, `25-system-email`, `26-system-connections`, `27-system-oidc`, `28-default-account-warning` | UC-020 – UC-025, UC-021. Nothing here edits configuration (C-012) |
| 13 | Reading the console in your language | `29-language-selector` | UC-026 — the selector only; making a language available is the installation manual's (BR-007) |
| 14 | Where to look next | — | the installation manual, the author's manual, the System overview |

Administrator-only procedures (chapters 3, 4, 5, 10, 11, 12) carry a marker in the margin, so the
one document serves both roles (BR-006, A5).

## Build wiring

`docs/manual/build-manual.sh` is Author's script with `JOB`/`PDF_NAME` changed to
`elicit-admin-manual`. It typesets inside a TeX Live container, so no TeX installation is needed;
`SKIP_MANUAL=1` skips it and the application then simply does not offer the entry (A1).

`buildDockerImage.sh` gains the three lines Author has, before `./mvnw clean package`:

```sh
VERSION="$(sed -n '1,20p' pom.xml | grep -m1 -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g')"
BUILD_DATE="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
./docs/manual/build-manual.sh "${VERSION}" "${BUILD_DATE}"
```

so the PDF is on the classpath when Quarkus builds the image and carries this image's stamp
(NFR-017, BR-003). `buildNativeDockerImage.sh` gets the same lines.

`.gitignore` gains `docs/manual/build/`, `docs/manual/build-info.tex`, `src/main/resources/manual/`,
`docs/manual/capture/node_modules/` and `docs/manual/capture/package-lock.json`.

## Application wiring

- `com.elicitsoftware.admin.manual.AdminManual` — an `@ApplicationScoped` bean over the classpath
  resource `manual/elicit-admin-manual.pdf`, with `isAvailable()` and `open()`, and an overridable
  `admin.manual.resource` so a test can exercise the build that carries no manual (A1).
  Deliberately **not** under `META-INF/resources`, which Quarkus would serve with no role check.
- `com.elicitsoftware.admin.manual.ManualResource` — `@Path("/manual")` under the existing
  `@ApplicationPath("/api")`, producing `application/pdf`, `Content-Disposition: inline` so the
  browser's viewer opens it and the reader can print or save (A2). Gated
  `@RolesAllowed({ElicitRoles.ADMIN, ElicitRoles.USER})` — the difference from Author, which allows
  one role (BR-006, A4). 404 when the build carries no manual.
- `MainLayout` — a `SideNavItem` above Logout and a header link, both `setRouterIgnore(true)` (the
  Vaadin router would otherwise swallow `/api/manual`) and opening in a new tab. Shown only when
  `AdminManual.isAvailable()` (A1). `VaadinIcon.FILE_TEXT_O`, as Author uses.
- `MissingDepartmentDialog` — add the manual link beside its existing action (A7), guarded the same
  way. This is the only behavioural change to an existing screen, and it keeps the notice's promise
  of a reachable action (NFR-016) pointing at the document that explains the fix.

## Translations

NFR-014 fails the build on a hard-coded literal, so the three entries are keys, not strings:
`mainLayout.nav.manual`, `mainLayout.header.manual`, `mainLayout.header.manualTitle`, plus
`missingDepartmentDialog.manual` for the dialog link (which resolves through `Translations.get`, as the rest of that dialog does). Each needs a line in
`src/main/resources/vaadin-i18n/translations.properties` **and** in
`translations.context.properties` (screen | purpose | max length | note), then Spanish and Arabic
in `elicit-i18n/admin/translations_es_419.properties` and `…_ar.properties`, and a regenerated
`i18n/TRANSLATION_REQUEST.md`. The manual itself stays English (BR-009).

## Tests

Traceable to UC-029 by name and comment, following Author's three:

| Test | Asserts |
| --- | --- |
| `AdminManualTest` | the packaged resource is found and opens; `isAvailable()` is true in a build that carries it |
| `ManualResourceTest` | `/api/manual` answers `application/pdf` to `elicit_admin` **and** to `elicit_user` (BR-006), and refuses a reader holding neither (A4) |
| `MissingManualTest` | with `admin.manual.resource` pointed at nothing: 404, and `isAvailable()` false so the navigation omits the entry (A1) |

A fourth, `ManualNavigationTest`, asserts the dialog of UC-028 offers the manual link when the
manual is available and omits it when it is not (A7).

The tests read `src/test/resources/manual/elicit-admin-manual.pdf`, a committed fixture, so the
suite never needs Docker or LaTeX.

## Capturing the figures

`docs/manual/capture/capture-screenshots.mjs` drives Playwright against a running Admin on the
umbrella stack (`ADMIN_URL`, default `http://localhost:8081`), signs in through Keycloak and walks
the chapters above. Unlike Author's capture, which needs a specific sample survey, this one needs a
**seeded console state**: one department, one non-default user with each role, one message
template, at least one registered subject with a report, and the Family History Survey applied. The
script creates what is missing so it can run against a freshly reset stack, and the README records
the state it leaves behind.

Figures are committed and regenerated only when the screens change. After regenerating, check that
every `\screenshot{…}` caption still matches what the figure shows — the caption is part of the
instruction.

## Order of work

1. `docs/manual/` skeleton: `elicit-brand.sty`, `build-manual.sh`, `README.md`, a `.tex` with the
   title page and chapter headings but no figures. Build it once to prove the TeX Live path works.
2. `AdminManual`, `ManualResource`, the three tests, the committed fixture. Nothing visible yet.
3. `MainLayout` entries, the dialog link, the translation keys in all five files.
4. The capture script and the figures.
5. Write the chapters against the captured figures.
6. Wire `buildDockerImage.sh` / `buildNativeDockerImage.sh`, build the image, confirm the entry
   appears for both roles and the stamp on the title page and the footers is this build's.
7. Flip FR-036, NFR-017, C-017 and the use case's `Status` to Implemented.

## Decisions taken

- **Gated only.** No unauthenticated path, and the built PDF is not committed. A reader who cannot
  sign in yet is the installation manual's reader, not this one's.
- **One document for both roles**, with administrator-only procedures marked, rather than two
  builds (BR-006).
- **The umbrella installation manual is a separate use case** and is written next; this manual
  refers to it and must not start repeating it.
- **The boundary is the action, not the screen.** If a procedure provisions the deployment it is the
  installation manual's, even when it is performed from a console screen — which is what puts
  language management (UC-027, Author UC-041) there and leaves only the language selector here.

## Follow-on

The umbrella installation manual has landed: the umbrella repository now carries its first AIUP
artifacts (`docs/vision.md`, `docs/requirements.md`, `docs/use_cases.puml`, `docs/use_cases/UC-001`
… `UC-019`) and the manual itself under `docs/manual/`. The pieces that were handed to it:

| Umbrella | Owns |
| --- | --- |
| UC-016 Add, Replace or Remove a Language | the whole add/replace/remove procedure, for all three apps (manual §11.3) |
| UC-015 Mount the Translations Directory | the mount, `i18n.file.system.path`, the per-key resolution order |

Its chapter §11.3 documents the server-side file procedure as the operative one and marks Admin
UC-027 and Author UC-041 as specified but not built in this release — which is consistent with
BR-008 here.

Chapter 14 of this manual therefore links to it by name, and its own "after installation" chapter
hands the reader back here.
