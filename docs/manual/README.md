# The administrator's manual (UC-029)

`Running the Console` — the printable, illustrated manual that ships inside the
application image and is opened from the console's navigation. It documents the
console of an installed system: signing in, the first department, users and
roles, message templates, registering subjects and issuing access codes,
invitations and reminders, searching and monitoring progress, reports, moving a
respondent between deployments, applying and exporting a survey definition, and
the System screens.

See [`../use_cases/UC-029-consult-the-administrators-manual.md`](../use_cases/UC-029-consult-the-administrators-manual.md)
for the behaviour this implements, [`../plan/UC-029-administrators-manual.md`](../plan/UC-029-administrators-manual.md)
for the plan it follows, and FR-036 / NFR-017 / C-017 in
[`../requirements.md`](../requirements.md).

## Status: this is an outline build

`elicit-admin-manual.tex` is **not finished**. It currently carries the title
page, the 14 chapter headings, and a temporary `\outline{…}` macro under each
chapter saying what that chapter will cover. The build is real — it typesets and
produces a PDF — but the procedures and their figures are not written yet.

The change that finishes the manual **deletes the `\outline` macro definition
along with its uses**, so the build fails on any chapter that was missed rather
than shipping a placeholder.

## What is here

| Path | Committed? | What it is |
| --- | --- | --- |
| `elicit-admin-manual.tex` | yes | the manual |
| `elicit-brand.sty` | yes | the Elicit default brand as LaTeX — colours, IBM Plex, the page furniture, `\screenshot`, `\ui`, `\term` (UC-029 BR-005) |
| `images/` | yes | the captured screens, plus `elicit-logo.png` for the title page |
| `capture/` | yes | `capture-screenshots.mjs`, its own `package.json`, and `fixtures/` — the survey definition and the three finished respondents the walk installs; regenerates `images/` from a running Admin |
| `build-manual.sh` | yes | typesets the PDF and puts it where the application packages it |
| `README.md` | yes | this file |
| `build-info.tex` | **no** | generated: the version and build date stamped into this build |
| `build/` | **no** | generated: LaTeX's working directory and the PDF |
| `../../src/main/resources/manual/` | **no** | generated: where the image picks the PDF up |
| `../../src/test/resources/manual/elicit-admin-manual.pdf` | yes | a small fixture, so the tests need neither Docker nor LaTeX |

### `elicit-brand.sty` is Author's file, adjusted — not a verbatim copy

The style file came from `Author/docs/manual/elicit-brand.sty` with **two**
changes, and it must keep them:

1. the product name in the page footer — `Elicit Author` → `Elicit Admin`;
2. the use-case references in its comments — `UC-039` → `UC-029`.

It is duplicated rather than shared because Admin and Author are independent
repositories with no common build. It is the one file to **re-copy from Author
and re-apply those two changes to** when the brand changes.

## Building it

```bash
docs/manual/build-manual.sh                       # version from pom.xml, date is now
docs/manual/build-manual.sh 3.0.0 "2026-09-24"    # explicit stamp
```

`buildDockerImage.sh` (and `buildNativeDockerImage.sh`) calls it with the project
version and the build date before `mvn package`, so the PDF is on the classpath
when Quarkus builds the image. The PDF lands at
`src/main/resources/manual/elicit-admin-manual.pdf` — deliberately **not** under
`META-INF/resources`, which Quarkus would serve with no role check. It reaches
the browser only through `ManualResource` (`/api/manual`), which requires
`elicit_admin` **or** `elicit_user` (UC-029 BR-006, A4).

There is no TeX installation to maintain: the script typesets inside a TeX Live
container (`TEXLIVE_IMAGE`, default `texlive/texlive:latest`), which is where IBM
Plex and the LaTeX packages come from. Docker must be running.

`SKIP_MANUAL=1` builds the application without the manual. The application then
simply does not offer the entry (UC-029 A1) — nothing else changes.

## The version stamp

`build-manual.sh` writes `build-info.tex`, which `elicit-brand.sty` reads. The
version and build date appear on the title page and in the footer of **every**
page (NFR-017). A build that supplies neither leaves both reading `unknown`
rather than omitting them (UC-029 A3, BR-003), so a printed copy is never
silently undated.

## Regenerating the screenshots

The figures are committed. Regenerate them only when the screens they show
change. All 29 come from one run against a **greenfield** stack:

```bash
cd …/Elicit && ./resetDatabase.sh V3 && docker compose up -d
npm --prefix Admin/docs/manual/capture install      # playwright
npm --prefix Admin/docs/manual/capture run capture
```

The capture tool keeps its own `package.json` so that Playwright stays out of the
Vaadin-managed one at the project root.

The script drives a running Admin, signs in through Keycloak and walks the
chapters, writing `images/NN-*.png` at 1440×900 ×2. Unlike Author's capture,
which needs a specific sample survey, this one needs a **seeded console state**:
one department, one non-default user with each role, one message template, a
registered subject, a survey, and a respondent who has finished it. It creates
all of that on its way through, so it can run against a freshly reset stack, and
it leaves the state behind in the console it ran against.

Env: `ADMIN_URL` (default `http://localhost:8081`), `ADMIN_USER`,
`ADMIN_PASSWORD`, `MANUAL_IMAGES`, `MANUAL_SURVEY_DEFINITION`, `MANUAL_SURVEY`.

After regenerating, check that every `\screenshot{…}` in the `.tex` still matches
what the figure shows — the caption is part of the instruction.

### Why a greenfield stack, and what `fixtures/` is for

Three figures are states a working console cannot be put back into:

| figure | the state it needs |
| --- | --- |
| `03-department-required` | an account with no department — only before the first one exists (UC-028) |
| `21-missing-survey-notice` | a deployment with no survey applied (UC-019) |
| `15-report-download` | a respondent whose status is Finished (UC-005) |

The first two are why the run starts from `resetDatabase.sh V3`: the script takes
them before it creates the department and before it applies the definition. On a
stack that is already set up it says so and leaves the committed figure alone.

The third cannot be produced by the console at all — the answers are given in the
Survey app — so `capture/fixtures/` carries them:

| fixture | what it is |
| --- | --- |
| `household-survey.elicit` | the survey the figures install: the Household Survey from the umbrella's multi-site e2e run, two steps and six questions, small enough to read in a figure |
| `respondent-marcus-bell.elicit`, `respondent-nadia-okafor.elicit`, `respondent-theo-lindqvist.elicit` | three respondents who finished it, exported from that same run (`ELICIT_EXPORT_V2`) |

`ensureFinishedRespondents()` imports the three when the deployment has no
finished respondent. Their subjects carry the department **code** the walk
creates (`MANUAL`), because the import matches a subject's department by code and
never creates one (BR-102) — change `DEPARTMENT.code` and the fixtures have to
change with it. The names and that code are the only edits made to the exports;
the answers are the e2e run's own.

The definition the figures install is deliberately *not* the Family History
Survey: the capture then depends on nothing outside this repository.
`MANUAL_SURVEY_DEFINITION=…/FHHS/family-history-survey.elicit` switches it back
in an umbrella checkout, but the respondent fixtures no longer match that survey,
so figure 15 falls back to whatever the deployment already has. `MANUAL_SURVEY`
(the name the Register and Export screens list, and the name the example message
template uses) is read from the definition's `# survey_name:` header, so it
follows automatically.

The screenshots the e2e suites leave in `e2e-tests/target/` and
`e2e_multisite/target/` are **not** a source for any figure. They are ad-hoc
failure and probe captures at 1440×1024 — 1×, where the manual's figures are 2×
for print.
