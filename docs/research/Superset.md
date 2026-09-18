# Embedding Apache Superset in Elicit Admin

> **Status (2026-09-18):** Research. Nothing described here is implemented. This document
> records what was learned about [Apache Superset](https://superset.apache.org/), how it
> would attach to the existing `surveyreport` star schema, how an **Analytics** role and
> left-menu item would reach it from Admin, which out-of-the-box visualizations to ship,
> and how report-ready images can be produced. Decisions still open are collected in
> section 10.

## Overview

The request has four parts:

1. **Left menu.** Admin's drawer gets a new **Analytics** item. It is shown only to users
   holding a new analytics role, and it takes them to the Apache Superset UI.
2. **Data.** Superset works on the **reporting schema** (`surveyreport`) and its
   **star schema** (the `dim_*` and `fact_*` tables plus the views built over them). It
   never touches the transactional `survey` schema.
3. **Out-of-the-box visualizations.** A set of dashboards and charts ships with the
   platform and is present on first start, not built by hand per deployment.
4. **Report-ready images.** Charts and dashboards can be exported as images suitable for
   dropping into a report or manuscript.

Admin's `docs/vision.md` already places "cross-survey analytics dashboards" out of scope
for Admin itself and hands them to "downstream reporting tools". Superset is that tool.
Admin's job is therefore limited to **authorising and navigating** to it; the analytics
themselves live in Superset and its shipped assets.

## 1. What Superset Is

| Fact | Value |
|---|---|
| Project | Apache Superset, Apache-2.0 licensed, ASF top-level project |
| Current release | 6.1.0 (PyPI, 2026-05-13); Python 3.10–3.12 |
| Runtime | Python / Flask-AppBuilder web app, Gunicorn; React front end |
| Own state | A **metadata database** (PostgreSQL is supported and is what we would use) holding users, roles, datasets, charts, dashboards, logs |
| Async work | Celery worker + Celery beat, backed by Redis, needed for Alerts & Reports, thumbnails, and async queries |
| Data access | SQLAlchemy URIs; `psycopg2` for PostgreSQL is bundled in the official image |
| Image | `apache/superset:6.1.0`; a small custom image is needed to add `Authlib` (OIDC), `playwright` + Chromium (server-side screenshots) and `Pillow` (PDF) |
| Security model | Flask-AppBuilder RBAC. Built-in roles `Admin`, `Alpha`, `Gamma`, `Public`, `sql_lab`; custom roles are composed from fine-grained permissions (`can_read on Dashboard`, `datasource access on [db].[schema].[table]`, ...) |

Two Superset features matter most for this work:

- **OAuth / OIDC login** (`AUTH_TYPE = AUTH_OAUTH`) with **role mapping**
  (`AUTH_ROLES_MAPPING`, `AUTH_ROLES_SYNC_AT_LOGIN`) so Keycloak stays the single source
  of identity.
- **Embedded dashboards** (`EMBEDDED_SUPERSET` feature flag, guest tokens,
  `@superset-ui/embedded-sdk`) for rendering a dashboard inside another web app without a
  Superset login. This is optional for the first iteration (see section 3).

### 1.1 License

Apache Superset is licensed under the **Apache License, Version 2.0** (`LICENSE.txt` in
the repository; the only sub-components under a different license are the bundled Inter
and Fira Code fonts, SIL Open Font License 1.1). Elicit's modules are PolyForm
Noncommercial 1.0.0. The two coexist without conflict, for these reasons:

- **Apache-2.0 is permissive, not copyleft.** It allows use, modification and
  redistribution for any purpose, commercial or not, and places no requirement on the
  license of software that runs alongside or calls it. Elicit does not relicense Superset;
  Superset stays Apache-2.0 and Elicit stays PolyForm Noncommercial.
- **Deploying Superset as a separate service** (Option A or B) is plain use. Nothing in
  Admin links against Superset code, so no obligations attach to Admin at all.
- **Redistributing a modified image** (the proposed `elicitsoftware/superset` image with
  Authlib, Playwright and a `superset_config.py`) is redistribution under Apache-2.0
  section 4: ship Superset's `LICENSE.txt` and `NOTICE` with the image, keep existing
  copyright and attribution notices, and mark any Superset source files that were
  changed (configuration and shipped dashboard YAML are not modifications of Superset).
- **Shipped dashboards and datasets** are Elicit's own work product and can carry
  Elicit's license; they are data consumed by Superset, not derivative code.
- **Trademark.** "Apache Superset" and its logo are ASF trademarks. Describing the
  feature as "powered by Apache Superset" is fine; naming the Analytics item "Superset"
  or reusing its logo as if it were Elicit's is not. The Analytics menu label avoids the
  issue.
- **Dependencies.** ASF policy forbids GPL-licensed required dependencies in Apache
  projects, so the Python stack is permissive throughout. The one LGPL component,
  `psycopg2` (LGPL-3.0 with an OpenSSL exception), is already in the official image and
  is used unmodified as a separate library, which LGPL permits.
- **Patent clause.** Apache-2.0 grants a patent license from contributors and terminates
  it for anyone who sues over the covered work. This is standard and imposes nothing on
  Elicit as a user.

This is an engineering reading of the license terms, not legal advice. Because Elicit is
a University of Michigan project under a noncommercial license, confirm with the
university's technology transfer or licensing office before the first release that
includes a Superset image, and record their answer here.

## 2. The Data Superset Sees

### 2.1 The `surveyreport` star schema as it exists today

Survey owns the schema (`migration-v3/V002__Create_Reporting_Schema.sql`) and its ETL
(`com.elicitsoftware.etl.Sql`) extends it at runtime. The live local database currently
contains:

| Kind | Objects |
|---|---|
| Fixed dimensions | `dim_date` (1970-01-01 plus every day 2020-01-01 → 2029-12-31), `dim_status` (Not Started / In Progress / Finished), `dim_step`, `dim_section` |
| Survey-driven dimensions | One `dim_<name>` per row of `survey.dimensions`; for FHHS today: `dim_age`, `dim_ashkenazi`, `dim_cancer`, `dim_gender`, `dim_generation`, `dim_latinx`, `dim_multiple_cancers`, `dim_other_cancer_name`, `dim_race`, `dim_relationship`, `dim_shared_parent`, `dim_sibling_type`, `dim_triple_negative_breast_cancer`, `dim_vital_status` |
| Fact tables | `fact_respondents` (one row per respondent: created/first-access/finalized date keys, `active`, `logins`, `status`, `duration`), `fact_sections` (one row per section instance visited; the ETL **adds a `<dim>_key` FK column per survey dimension**) |
| Views built by Survey's ETL | `fact_respondents_view` (date keys resolved to `dim_date.datename`, status resolved to text), `fact_sections_view` (every `<dim>_key` resolved to its text value) |
| Views built by a survey module | FHHS adds `surveyreport.fact_fhhs_view` (`FHHS/.../V0.0.3__CREATE_FHHS_FACT_VIEW.sql`): one row per family member with cancer columns pivoted out of `fact_sections_view` |

A read-only role already exists for exactly this purpose: **`surveyreport_user`** holds
`SELECT` on every relation in `surveyreport` (22 grants on the live database, including
both views, and the ETL re-grants `fact_sections_view` each time it is rebuilt). It has no
grants on the `survey` schema. Superset's database connection should use this role and
nothing more.

### 2.2 Characteristics that shape the Superset datasets

- **`fact_sections_view` is rebuilt with `DROP VIEW ... CASCADE`** whenever the ETL finds a
  new dimension. That also drops `fact_fhhs_view` until FHHS restarts (this is the reason
  for the umbrella's three-pass first start). Superset **physical datasets** pointing at a
  view survive the drop (they are just metadata) but their column list goes stale; a
  "Sync columns from source" is needed after a survey dimension is added. Prefer physical
  datasets on the fact and dimension **tables**, and keep view-based datasets to the few
  places where the pivoted shape is genuinely needed (FHHS).
- **Every resolved column in the views is `varchar(255)`**, including ages
  (`age`, `breast_cancer_age`, ...). FHHS's own view casts them `::Numeric`. Any chart that
  aggregates an age must do the same, either in a virtual (SQL) dataset or in a
  calculated column/metric on the dataset.
- **Dates in `fact_respondents_view` are the display string `datename` (`varchar(11)`)**,
  not a date. For time series, join the fact table to `dim_date` and use `fulldate`, or
  define a virtual dataset that does so. `dim_date` stops at 2029-12-31; a Survey
  migration extending it will be needed before 2030 regardless of Superset.
- **There is no `dim_survey`.** `survey_id` is a bare integer and `surveyreport_user`
  cannot read `survey.surveys` for a title. Either add a narrow grant on
  `survey.surveys(id, title)` or have the ETL maintain a `dim_survey`. Until then,
  dashboards label surveys by id.
- **Free text is present.** `fact_sections.name` carries the respondent-entered label for
  a repeated step instance (a relative's name in FHHS), and `other_cancer_name` is free
  text. These are potentially identifying and should be **excluded** from the datasets
  the analytics role can see (dataset column visibility) rather than relying on chart
  authors to avoid them.
- **Per-answer detail and question versions are not in the star schema.** The grain is
  section-instance and the dimension columns are keyed by ontology tag, so nothing in
  `surveyreport` says which question, or which Type 2 version of it, produced a value.
  Section 6.2.2 proposes a Survey-owned bridging view keyed by the same metadata tags.
- **`fact_respondents` is populated only for survey 1.** The insert and update triggers
  in `V002__Create_Reporting_Schema.sql` test `new.survey_id = 1`, so respondents of any
  other survey never get a fact row. Any cross-survey chart needs that trigger generalised
  first.
- **Row counts are small and the star is well indexed** (Survey `V007`/`V008`). No
  caching layer is needed for correctness; Superset's per-chart cache is a nicety.

## 3. Integration Options

### Option A — Navigation link with single sign-on (recommended first)

Admin renders an **Analytics** `SideNavItem` whose path is Superset's base URL. Vaadin 25.2
supports this directly: `SideNavItem(String label, String path, Component prefix)` accepts
an absolute URL, `setRouterIgnore(true)` keeps Flow's router out of the way, and
`setOpenInNewBrowserTab(true)` opens Superset in its own tab. Superset authenticates the
user against Keycloak; because the user already has a Keycloak session from logging into
Admin, the redirect completes without a second password prompt.

- Pros: smallest change (one nav item, one config property, one role), Superset's full UI
  is available (Explore, dashboards, export menus, saved queries), no cross-site cookie
  or CSP work.
- Cons: visually a separate application; theming must be done on the Superset side to
  keep the Elicit brand (Superset 6 supports `THEME_DEFAULT` tokens for logo, app name,
  colours and fonts, which can be fed from `elicit-brand/brand-config.json`).

### Option B — Embedded dashboards inside a Vaadin view (later)

Superset's `EMBEDDED_SUPERSET` feature flag turns on **guest tokens**. Admin's backend
calls `POST /api/v1/security/guest_token/` as a Superset service account holding
`can_grant_guest_token`, passing the dashboard ids (and optional row-level-security
clauses and user attributes). A Vaadin view mounts an `<iframe>` through the
`@superset-ui/embedded-sdk` (`embedDashboard({ id, supersetDomain, mountPoint,
fetchGuestToken, dashboardUiConfig })`). Superset must list Admin's origin in the
dashboard's **allowed domains** and in `TALISMAN_CONFIG` → `frame-ancestors`, and its
session cookie needs `SameSite=None; Secure` behind HTTPS.

- Pros: an "Analytics home" inside Admin with the brand header and drawer; guests never
  see Superset chrome; RLS per department becomes possible.
- Cons: read-only (no Explore, no editing, no SQL Lab), guest tokens expire and must be
  refreshed, one more secret, and every embedded dashboard has to be registered
  individually. Per-chart download controls are still available unless
  `hideChartControls` is set, so image export survives embedding.

### Option C — Reverse-proxy Superset under an Admin path (not recommended)

Serving Superset at `/analytics/*` from Admin's origin would avoid cross-site cookies,
but Quarkus/Vaadin is not a proxy, Superset's absolute asset URLs need a matching
`APPLICATION_ROOT`, and it doubles the surface area of the Admin container. A front
proxy (nginx/Traefik) in production may still put both apps under one host name, but that
is deployment topology, not an Admin feature.

**Recommendation:** ship Option A. Design the role and configuration so Option B can be
added as a second Analytics view later without changing what was built for A.

## 4. Identity, the Analytics Role, and Keycloak

### 4.1 Role name

The request calls the role "Analytics_user". Admin's existing roles are `elicit_admin`,
`elicit_user`, `elicit_importer` (`ElicitRoles`), and Author's is `elicit_author`, so the
proposed name is **`elicit_analytics`**. It is a Keycloak *client role* like the others.

### 4.2 Where the role sits in Admin's hierarchy

`ElicitRoles` today is a strict ladder: `elicit_admin` ⊃ `elicit_user` ⊃
`elicit_importer`. Analytics is orthogonal: a researcher may hold it without being able
to register subjects, and a survey administrator may not hold it. Two consequences:

- Add `ANALYTICS = "elicit_analytics"` to `ElicitRoles.ALL` but **not** to `IMPLIED`
  for any existing role. Whether `elicit_admin` should automatically imply it is an open
  decision (section 10); the conservative default is that it does not, so that PHI-bearing
  aggregates are granted deliberately.
- `MainLayout.createNavBar()` shows the Analytics item when
  `identity.hasRole(ElicitRoles.ANALYTICS)`; it is neither inside the Admin section nor
  gated on `elicit_admin`.

### 4.3 The `DATABASE` authorization mode does not reach Superset

Admin can source roles from `survey.user_roles` instead of the token
(`elicit.authorization.mode=DATABASE`, `RoleSecurityIdentityAugmentor`). That mode only
governs Admin. Superset logs the user in through Keycloak and reads roles from the
token, so **the analytics role must be assigned in Keycloak** for the link to lead
anywhere useful. In `DATABASE` mode the Admin menu item can be driven from
`survey.user_roles`, but a user without the Keycloak role would land in Superset with
whatever `AUTH_USER_REGISTRATION_ROLE` gives them (recommended: a role with no dataset
access). The Edit User screen (`EditUserView`) also stores a **single** role per user in
a `ComboBox`; a second, orthogonal role needs either a multi-select there or a decision
that analytics is Keycloak-only.

### 4.4 Keycloak changes (umbrella `keycloak/elicit-realm.json`)

| Change | Detail |
|---|---|
| New client | `elicit-superset`, confidential, redirect URI `http://localhost:8088/oauth-authorized/keycloak`, web origin `http://localhost:8088` |
| New client roles | `elicit_analytics` on `elicit-superset` (or on `elicit-admin`, see section 10). Optionally `elicit_superset_admin` for the person who curates dashboards |
| Mapper | A `oidc-usermodel-client-role-mapper` on `elicit-superset` writing the client's roles to a flat `roles` claim in the **userinfo** response (Author already has the `resource_access.${client_id}.roles` variant; Superset's security manager reads userinfo, so a flat claim is simpler to consume) |
| Test users | `analyst` (analytics only) in addition to `admin` and `user`; give `admin` the analytics role too so the existing local login can reach Superset |

### 4.5 Superset side (`superset_config.py`)

```python
from flask_appbuilder.security.manager import AUTH_OAUTH
AUTH_TYPE = AUTH_OAUTH
OAUTH_PROVIDERS = [{
    "name": "keycloak", "icon": "fa-key", "token_key": "access_token",
    "remote_app": {
        "client_id": "elicit-superset",
        "client_secret": os.environ["OIDC_CLIENT_SECRET"],
        "server_metadata_url": os.environ["OIDC_SERVER_METADATA_URL"],
        "client_kwargs": {"scope": "openid email profile"},
    },
}]
AUTH_USER_REGISTRATION = True           # first login creates the Superset user
AUTH_USER_REGISTRATION_ROLE = "Public"  # no data access unless a mapped role applies
AUTH_ROLES_SYNC_AT_LOGIN = True         # re-read Keycloak roles on every login
AUTH_ROLES_MAPPING = {
    "elicit_analytics":      ["ElicitAnalyst"],
    "elicit_superset_admin": ["Admin"],
}
CUSTOM_SECURITY_MANAGER = ElicitSecurityManager  # oauth_user_info() returns role_keys from the userinfo "roles" claim
```

`ElicitAnalyst` is a custom Superset role: `Gamma` minus SQL Lab, plus
`datasource access` on each curated dataset, plus `can_export_image` /
`can_export_data` if `GRANULAR_EXPORT_CONTROLS` is enabled. It is created by the
init step (section 7) so it exists before the first login.

The same Keycloak URL lesson that Author documented applies: the metadata URL must
resolve from **both** the Superset container (token exchange) and the browser (redirect),
so locally it is `http://host.docker.internal:8180/realms/elicit/.well-known/openid-configuration`,
not the compose-internal `keycloak:8080`.

## 5. The Left-Menu Item in Admin

Concrete change in `MainLayout.createNavBar()`:

```java
if (identity.hasRole(ElicitRoles.ANALYTICS) && analyticsConfig.url().isPresent()) {
    SideNavItem analytics = new SideNavItem("Analytics", analyticsConfig.url().get(),
            VaadinIcon.CHART.create());
    analytics.setRouterIgnore(true);
    analytics.setOpenInNewBrowserTab(true);
    nav.addItem(analytics);
}
```

Supporting pieces:

- `elicit.analytics.url` (a `@ConfigMapping`, empty by default). When unset, the item is
  hidden even for role holders, so deployments without Superset are unaffected. The value
  is the **browser-reachable** Superset URL (`http://localhost:8088` locally), not a
  compose-internal name.
- `ElicitRoles.ANALYTICS` and the `EditUserView` role list (section 4.3).
- A single Superset landing page: `elicit.analytics.url` can point at
  `/superset/dashboard/elicit-overview/` so the user opens on the shipped overview
  dashboard rather than Superset's welcome page.
- AIUP artifacts: `FR-020 Open Analytics` in `docs/requirements.md`, a
  `UC-020-open-analytics.md` use case, and the `Analyst` actor in `docs/use_cases.puml`.
  The use case is deliberately thin (precondition: role; main flow: click, SSO, land on
  overview; alternative: URL not configured → item absent).

## 6. Out-of-the-Box Visualizations

### 6.1 Ownership follows the schema

The star schema has a platform-generic half (`fact_respondents`, `dim_date`,
`dim_status`, `dim_step`, `dim_section`) and a survey-specific half (the `dim_<name>`
tables and `fact_fhhs_view`). Shipped dashboards should split the same way:

| Dashboard | Owner / repo | Datasets |
|---|---|---|
| **Survey Operations** (generic, every deployment) | umbrella `superset/assets/` (platform infrastructure, like the compose file) | `fact_respondents` ⋈ `dim_date` ⋈ `dim_status`; `fact_sections` ⋈ `dim_step` ⋈ `dim_section` |
| **Answer metadata view** (`surveyreport.answer_metadata_view`, section 6.2.2) | Survey repo (Flyway migration; Survey owns both schemas and the grants) | `survey.answers` ⋈ `survey.questions` ⋈ `survey.metadata` ⋈ `survey.ontology` ⋈ `select_items`, exposed read-only |
| **Family Health History** (FHHS deployments) | `FHHS/superset/assets/` (shipped with the survey that defines the dimensions, as `fact_fhhs_view` is) | `fact_fhhs_view` (virtual dataset with numeric casts), the FHHS `dim_*` tables |

Admin ships no dashboards of its own; it ships the door.

### 6.2 Survey Operations dashboard (proposed charts)

| Chart | Type | Source |
|---|---|---|
| Respondents registered per week / month | Time-series bar | `fact_respondents.created_key` → `dim_date.fulldate` |
| Completion funnel: registered → first access → finished | Funnel / big-number trio | `first_access_key <> 19700101`, `status` |
| Status breakdown | Pie or donut | `dim_status.value` |
| Median and distribution of completion time | Box plot / histogram | `fact_respondents.duration` (interval → seconds in a calculated column) |
| Days from invitation to first access | Histogram | `first_access_key − created_key` via `dim_date.fulldate` |
| Logins per respondent | Histogram | `fact_respondents.logins` |
| Section coverage: respondents reaching each step/section | Heatmap | `fact_sections` ⋈ `dim_step` ⋈ `dim_section`, count distinct `respondent_id` |
| Active vs inactive respondents | Big number with trend | `fact_respondents.active` |

Filters: survey (by `survey_id` until a `dim_survey` exists), created-date range, status.

### 6.2.1 First visualization: respondent funnel

The first chart to build is a funnel answering "how many respondents are registered, how
many are in progress, and how many have finished". A funnel needs **cumulative** stages
(each stage a subset of the one before), so the chart shows:

| Stage | Definition | `fact_respondents` predicate |
|---|---|---|
| Registered | Every active respondent | `active` |
| Started | Has accessed the survey at least once (in progress **or** finished) | `active AND status >= 1` |
| Finished | Finalized | `active AND status = 2` |

The literal three-way split (Not Started / In Progress / Finished) is shown next to the
funnel as a **status breakdown** chart. "In progress" on its own is not a superset of
"finished", so putting it in the funnel would make the funnel widen at the last step.

`status` is maintained by the `insert_fact_respondent` / `update_fact_respondent`
triggers in Survey's `V002__Create_Reporting_Schema.sql`: `0` when there is no first
access, `1` after first access, `2` once finalized. `dim_status` carries the labels.

**Dataset.** A virtual (SQL) dataset `respondent_funnel` on the reporting connection.
Grouping by `survey_id` keeps the dashboard's survey filter working:

```sql
SELECT f.survey_id, s.stage, s.stage_order,
       COUNT(*) FILTER (
           WHERE s.stage_order = 1
              OR (s.stage_order = 2 AND f.status >= 1)
              OR (s.stage_order = 3 AND f.status = 2)
       ) AS respondents
FROM surveyreport.fact_respondents f
CROSS JOIN (VALUES ('Registered', 1), ('Started', 2), ('Finished', 3)) AS s(stage, stage_order)
WHERE f.active
GROUP BY f.survey_id, s.stage, s.stage_order
```

The `CROSS JOIN` against the stage list matters: a `UNION ALL` of three `GROUP BY`
queries returns **no row** for a stage whose count is zero, and the funnel silently loses
its last step. Verified on the local database as `surveyreport_user` (2026-09-18):
Registered 6, Started 1, Finished 0, all three rows present. The same session confirmed
the role cannot read `survey.respondents`.

**Companion status breakdown** (bar or donut on the same dashboard):

```sql
SELECT s.value AS status, r.survey_id, COUNT(r.id) AS respondents
FROM surveyreport.dim_status s
LEFT JOIN surveyreport.fact_respondents r ON r.status = s.id AND r.active
GROUP BY s.id, s.value, r.survey_id
ORDER BY s.id
```

**Superset chart settings.** Chart type *Funnel Chart*; dimension `stage`; metric
`SUM(respondents)`; sort by `stage_order` ascending (custom SQL sort, or order the dataset
and disable the chart's own sorting); label format "value (percent of first stage)"; no
time range. A *Big Number* trio (Registered / Started / Finished) can reuse the same
dataset with a filter on `stage`. Both queries run as `surveyreport_user` with the grants
that already exist.

**Caveats to carry onto the dashboard.**

1. The `fact_respondents` triggers are hard-coded to `survey_id = 1`; respondents of any
   other survey never reach the fact table, so the funnel is blind to a second survey
   until Survey generalises the trigger.
2. "Registered" counts `respondents`, not `survey.subjects`. They are 1:1 today (6 and 6
   locally), but the distinction matters if a subject can ever hold several respondents.
3. Deactivated respondents are excluded by `active`; the dashboard should say so.
4. A "registered between" filter needs a join from `created_key` to `dim_date.fulldate`.
   Not required for the first cut.

### 6.2.2 Second visualization: answers by metadata tag

The second chart lets an analyst pick a **metadata tag** (optionally narrowed to a survey
section) and see how the answers carrying that tag were distributed, as a bar chart,
**including expired question versions**, so that a change of question text can be
compared against the answer distribution before and after the change.

**Why metadata, not the question.** Survey's `survey.metadata` table attaches an
`survey.ontology` tag (for FHHS: `Gender`, `Relationship`, `Breast Cancer`,
`Breast Cancer Age`, ...) to exactly one survey element: a **question** (durable
`question_id`), a **question placement in a section** (durable `sections_question_id`), or a
step-section mapping (durable `steps_sections_id`). Because those keys are the durable
Kimball keys, a tag survives every new version of a question and can also be carried by
more than one question over time (a retired question replaced by a new one with the same
tag). The tag is therefore the stable analytical identity; the question text is only one
version's wording of it. The ETL already uses the same mapping to build the
`dim_<tag>` tables, so the chart answers "how was *Gender* answered" the way the star
schema already thinks, while keeping the per-version detail the star drops.

Metadata semantics that the view must honour (from `Survey/.../etl/Sql.java`,
`FIND_DIMENSTION_VALUES_SQL`):

- `metadata.value IS NULL` means "the answer's own value is the tag's value" (a question
  tagged `Gender` contributes the selected option).
- `metadata.value` non-null is a **constant** implied by answering at all (in FHHS,
  answering question 4 implies `Vital Status = 'Alive'`, question 40 implies `'Deceased'`).
- Placement-level tags let one reusable question mean different things in different
  sections (the same "age at diagnosis" question is `Breast Cancer Age` in one placement
  and `Lung Cancer Age` in another).
- Step-section tags (`steps_sections_id`) describe the step itself (the "Mother" step
  implies `Gender = Female`), not an answer. They are already in `fact_sections` as
  dimension columns and are **out of scope** for this view.
- `ontology.dimension` groups related tags into one dimension table (`age`, `cancer`,
  `multiple_cancers`); it is exposed as `dimension_name` for filtering families of tags.

**Why the star schema cannot serve it.** `surveyreport` resolves each tag to a dimension
id at section-instance grain and keeps no question identity or version. The data the
chart needs lives only in `survey.answers ⋈ survey.questions ⋈ survey.metadata`, and
`surveyreport_user` has no grants on the `survey` schema. Three facts from Survey's
Type 2 design make the chart straightforward once that gap is bridged:

- `survey.answers.question_id` is the **surrogate** `survey.questions.id`, i.e. the exact
  version row the respondent saw (the FK was deliberately not retargeted by `V010`).
  Joining on it returns the durable `question_id`, `version`, `text`, `effective_from`
  and `effective_to` for every answer, retired versions included, with no as-of predicate.
- A retired version is any row with `effective_to < '9999-12-31 23:59:59+00'`. There is
  no status column (`is_draft` was dropped by `V015`). Admin's
  `SurveyDefinitionUpdateService.upsertQuestion()` opens a new version whenever `text` or
  any of a dozen other columns changes, closing the old row at `NOW()`.
- Choice answers store `select_items.coded_value`; the label is `select_items.display_text`
  and is not always equal to the code. Multi-select answers are comma-joined codes.

**The bridge: a Survey-owned view in `surveyreport`.** Following the precedent in
section 6.3, the join belongs in a Survey Flyway migration on both tracks (`migration/`
and `migration-v3/`, next free number after `V015`), granted to `${surveyreport_user}`.
PostgreSQL views run with their owner's privileges by default (`security_invoker` off),
and every `survey` table is owned by `elicit_owner`, so the reporting role needs a grant
on the view alone and still nothing on `survey.*`. The ETL's `DROP VIEW ... CASCADE` on
`fact_sections_view` does not touch this view because it does not depend on it.

Proposed `surveyreport.answer_metadata_view`, one row per **saved answer value × tag**
(a multi-select answer is unnested to one row per selected code; an answer carrying both
a question-level and a placement-level tag yields one row per tag):

```sql
CREATE OR REPLACE VIEW surveyreport.answer_metadata_view AS
WITH tagged AS (
    -- question-level tags: follow the durable question id
    SELECT m.id AS metadata_id, m.survey_id, m.ontology_id, m.value AS constant_value,
           'question'::text AS tag_scope, m.question_id, NULL::integer AS sections_question_id
    FROM survey.metadata m
    WHERE m.question_id IS NOT NULL
    UNION ALL
    -- placement-level tags: follow the durable section-question id
    SELECT m.id, m.survey_id, m.ontology_id, m.value,
           'placement', NULL, m.sections_question_id
    FROM survey.metadata m
    WHERE m.sections_question_id IS NOT NULL
)
SELECT a.survey_id,
       a.respondent_id,
       ds.value                                    AS respondent_status,
       o.id                                        AS ontology_id,
       o.name                                      AS ontology_name,
       o.tag,
       d.name                                      AS dimension_name,
       tg.tag_scope,
       sq.section_id,
       sec.name                                    AS section_label,
       q.question_id,
       q.question_key,
       ql.label                                    AS question_label,
       q.version                                   AS question_version,
       q.text                                      AS question_text,
       q.effective_from                            AS question_effective_from,
       q.effective_to                              AS question_effective_to,
       (q.effective_to = '9999-12-31 23:59:59+00') AS question_is_current,
       'v' || q.version || ' (' || to_char(q.effective_from, 'YYYY-MM-DD') || ' - '
           || CASE WHEN q.effective_to = '9999-12-31 23:59:59+00' THEN 'current'
                   ELSE to_char(q.effective_to, 'YYYY-MM-DD') END || ')' AS question_version_label,
       t.name                                      AS question_type,
       CASE WHEN tg.constant_value IS NOT NULL THEN tg.constant_value
            WHEN t.name IN ('TEXT', 'TEXTAREA', 'EMAIL', 'PASSWORD') THEN '(free text)'
            ELSE v.value_code END                  AS value_code,
       CASE WHEN tg.constant_value IS NOT NULL THEN tg.constant_value
            WHEN t.name IN ('TEXT', 'TEXTAREA', 'EMAIL', 'PASSWORD') THEN '(free text)'
            ELSE COALESCE(i.display_text, v.value_code) END AS value_label,
       (tg.constant_value IS NOT NULL)             AS value_is_constant,
       a.step_instance,
       a.section_instance,
       a.question_instance,
       a.saved_dt
FROM survey.answers a
JOIN survey.questions q            ON q.id = a.question_id
JOIN survey.question_types t       ON t.id = q.type_id
JOIN survey.sections_questions sq  ON sq.id = a.section_question_id
JOIN tagged tg                     ON tg.survey_id = a.survey_id
                                  AND (tg.question_id = q.question_id
                                       OR tg.sections_question_id = sq.sections_question_id)
JOIN survey.ontology o             ON o.id = tg.ontology_id
LEFT JOIN survey.dimensions d      ON d.id = o.dimension
LEFT JOIN LATERAL (                       -- one label per durable section, even after a rename
    SELECT s.name FROM survey.sections s
    WHERE s.section_id = sq.section_id
    ORDER BY s.version DESC LIMIT 1) sec ON TRUE
LEFT JOIN LATERAL (                       -- one label per durable question, across versions
    SELECT COALESCE(NULLIF(q2.short_text, ''), q2.text) AS label
    FROM survey.questions q2
    WHERE q2.question_id = q.question_id
    ORDER BY q2.version DESC LIMIT 1) ql ON TRUE
CROSS JOIN LATERAL unnest(
    CASE WHEN t.name IN ('MULTI_SELECT', 'CHECKBOX_GROUP')
         THEN string_to_array(a.text_value, ',')
         ELSE ARRAY[a.text_value] END) AS v(value_code)
LEFT JOIN survey.select_items i           -- label as of the moment the answer was saved
       ON i.select_group_id = q.select_group_id
      AND i.coded_value = v.value_code
      AND i.effective_from <= a.saved_dt
      AND i.effective_to   >  a.saved_dt
LEFT JOIN surveyreport.fact_respondents fr ON fr.id = a.respondent_id
LEFT JOIN surveyreport.dim_status ds       ON ds.id = fr.status
WHERE a.deleted = FALSE
  AND a.text_value IS NOT NULL
  AND a.saved_dt IS NOT NULL;

GRANT SELECT ON surveyreport.answer_metadata_view TO ${surveyreport_user};
```

| Column | Meaning |
|---|---|
| `ontology_id`, `ontology_name`, `tag`, `dimension_name` | The metadata tag the row is counted under; `dimension_name` (`age`, `cancer`, ...) groups families of tags and is null for stand-alone tags |
| `tag_scope` | `question` when the tag is attached to the durable question, `placement` when attached to the section-question placement |
| `section_id`, `section_label` | Durable section key and the name of its latest version, for an optional section filter |
| `question_id`, `question_key`, `question_label` | Durable question key, portable key, and the latest version's short text: shows which question(s) served the tag |
| `question_version`, `question_text`, `question_effective_from/to`, `question_is_current` | The exact version row the answer points at |
| `question_version_label` | `v2 (2026-03-01 - current)` style series key for the version comparison chart |
| `value_code`, `value_label`, `value_is_constant` | The value counted under the tag: the metadata constant when one is set, otherwise the answered code and its label; free-text types are masked as `(free text)` so the row still counts as answered but no content reaches the analytics role |
| `respondent_status` | Not Started / In Progress / Finished from `fact_respondents`, for a default "Finished only" filter |
| `respondent_id` | Kept for distinct counts; an opaque integer to the reporting role, which cannot reach `survey.subjects` |

Multi-select unnesting is applied before the constant check, so a constant-valued tag on a
multi-select question still yields one row per selected item; the dashboard's distinct
counts absorb that.

**Superset dataset and charts.**

- Physical dataset on the view with two metrics: `answers = COUNT(*)` and
  `respondents = COUNT(DISTINCT respondent_id)`. They differ for repeated steps (FHHS
  family members answer the same questions once per relative): read `respondents` for
  people and `answers` for instances.
- Dashboard native filters: `tag` (single select, required), `dimension_name` (optional,
  narrows the tag list to a family such as `cancer`), `section_label` (optional),
  `respondent_status` (default Finished), survey.
- **Chart A, "Answers by tag":** *Bar Chart*, x-axis `tag`, breakdown `value_label`,
  stacked, metric `respondents`, adhoc filter `question_is_current`. With the
  `dimension_name` filter set to `cancer` this is the cancer-prevalence bar from section
  6.3 for free; with no filter it is the whole survey's tag distribution.
- **Chart B, "Tag versions compared":** *Bar Chart*, x-axis `value_label`, series
  `question_version_label`, grouped bars, metric `respondents`, **contribution mode:
  series** so each version is normalised to 100% (the cohorts differ in size). Driven by
  the `tag` filter. Sort the series by the hidden `question_version` column, since the
  label sorts lexically. If a tag has been served by more than one durable question,
  add `question_label` as a second series dimension. A companion *Table* of
  `question_label`, `question_version_label`, `question_text`, `respondents` shows the
  reader exactly which wording each cohort saw.
- Tags whose `dimension_name` is `age` hold numbers as text; for those use a *Histogram*
  on `value_code::numeric` instead of Chart A.

**Caveats to carry onto the dashboard.**

1. Version cohorts split by the respondent's **first-access date**, not by when they
   answered: `QuestionManager.resolveAsOf` pins an in-progress respondent to the version
   current at first access, so they finish on the old text.
2. A question with no metadata row does not appear in this view at all. That is by
   design (the star schema ignores it too), but it means "every question in a section"
   is not what this chart shows; only tagged questions are.
3. Pre-Kimball answers carry `question_version = 0` and still join correctly through the
   surrogate id.
4. A retired select item keeps its label through the `saved_dt` range join; a code with
   no matching item at that instant falls back to the raw code.
5. `respondent_status` comes from `fact_respondents`, which the triggers populate only
   for survey 1 (section 2.2); the `LEFT JOIN` keeps other surveys' answers visible with a
   null status.
6. Value text is shown as answered, whereas the ETL lower-cases and trims it before
   building `dim_<tag>`. Counts here can therefore split on capitalisation that the star
   schema merges; apply `LOWER(TRIM(...))` in the view if parity with the dims matters.
7. **Not yet verified against data.** The local database is still on Survey's pre-Kimball
   track (Flyway `010`, no durable keys or `effective_*` columns, and `metadata` still
   holds surrogate `question_id` / `section_question_id`), so the SQL above was checked
   against the V3 DDL in `migration/V001__Create_Survey_Schema.sql` and `V010` section 12
   (the metadata rekey) only. It must be run on a V3 database before the migration is
   written. To demonstrate the version comparison, apply a survey definition with one
   changed question text through Admin (UC-017) and finish one respondent on each side
   of the change.

### 6.3 Family Health History dashboard (proposed charts)

| Chart | Type | Source |
|---|---|---|
| Relatives reported per respondent | Histogram | `fact_fhhs_view` grouped by `respondent_id` |
| Cancer prevalence by cancer type | Horizontal bar | one metric per `*_cancer = 'Yes'` column, or an unpivoted virtual dataset (recommended: one row per relative × cancer) |
| Prevalence by relationship and generation | Heatmap | `relationship`, `generation` vs cancer flags |
| Age at diagnosis by cancer type | Box plot | `*_cancer_age::numeric` |
| Ashkenazi / race / ethnicity composition | Stacked bar | `ashkenazi`, `race`, `latinx` |
| Multiple primaries | Table | `multiple_*` columns |

An **unpivoted virtual dataset** (`respondent_id, relative_id, relationship, generation,
cancer_type, age_at_dx`) makes most of these one-line charts and is far easier to keep
correct than 25 hand-written metrics. It should be added to FHHS as a proper
`surveyreport` view in a Flyway migration rather than living only in Superset, so that
`surveyreport_user` grants and the ETL's `CASCADE` behaviour are handled in one place.

### 6.4 Shipping the assets

Superset dashboards, charts, datasets and database connections export to a ZIP of YAML
files keyed by **UUID**. Those ZIPs are committed to the repos above and imported at start
by the init container with `superset import-dashboards -p <zip>` (or the
`POST /api/v1/assets/import/` API). Points to respect:

- The **database connection** is referenced by UUID from every dataset. Ship one
  `databases/elicit_reporting.yaml` with a fixed UUID and a `sqlalchemy_uri` that reads
  the password from the environment (`{{ env_var("SUPERSET_REPORTING_DB_PASSWORD") }}`
  is not supported in database YAML, so the init step patches the URI, or the connection
  is created by a small Python step and only datasets/charts/dashboards are imported).
- Import is idempotent with `overwrite=true`, so re-running the init container after an
  asset change is the upgrade path.
- The import needs the `surveyreport` tables to exist, so the init container must depend
  on Survey being healthy and, for FHHS assets, on FHHS having built its view. This slots
  into the umbrella's existing three-pass first start.
- Author dashboards once in a local Superset, export, commit. Do not edit the YAML by
  hand except for UUID/URI plumbing.

### 6.5 Brand

Superset 6 replaces `APP_NAME` with `THEME_DEFAULT` tokens (`brandAppName`,
`brandLogoUrl`, `colorPrimary`, `fontFamily`, `fontUrls`). A generated
`superset_config.py` fragment can derive these from `elicit-brand/brand-config.json`
and mount the logo from the same read-only brand volume Admin and Survey use.

## 7. Report-Ready Image Export

Superset offers three routes to an image. They differ in fidelity and infrastructure.

| Route | Output | Where | Needs | Fidelity |
|---|---|---|---|---|
| **Chart / dashboard menu → Download as image (PNG/JPEG) or PDF** | Client-side capture of the rendered DOM | Explore view and every chart's ⋮ menu on a dashboard, including embedded dashboards unless chart controls are hidden | Nothing extra | Screen resolution of the user's browser; adequate for slides, marginal for print |
| **Alerts & Reports** (`ALERT_REPORTS` feature flag) | Server-side PNG or PDF of a chart or whole dashboard, delivered by email (Mailpit locally) or Slack, on a schedule | Superset "Alerts & Reports" UI | Celery worker + beat, Redis, Playwright + Chromium in the image (`PLAYWRIGHT_REPORTS_AND_THUMBNAILS` is default in 6.x), `WEBDRIVER_WINDOW` sets the capture size, e.g. `{"dashboard": (1600, 2000), "slice": (800, 600)}` | Deterministic, headless, high-resolution; the right route for "report ready" |
| **Screenshot API** (`GET /api/v1/chart/{id}/cache_screenshot/`, `POST /api/v1/dashboard/{id}/cache_dashboard_screenshot/`) | Same server-side PNG, on demand, returned to a caller | Any authorised HTTP client, including Admin | Same Celery/Playwright stack | Same as above; lets Admin fetch figures programmatically |

Observations:

- Superset exports **raster only**. Charts are rendered by ECharts, which can emit SVG,
  but Superset exposes no vector export. If a journal requires vector figures, the
  fallback is CSV export from the same chart (`Download → Export to CSV`) and re-plotting
  outside Superset.
- `GRANULAR_EXPORT_CONTROLS` (6.1) splits `can_export_image`, `can_export_data` and
  clipboard permissions, so the analytics role can be allowed images but denied raw CSV
  if PHI policy requires it, or the reverse.
- The screenshot API opens a natural **phase 3**: Admin's existing subject-report pipeline
  (UC-005, `ReportService` → multi-page PDF) could pull cohort-level figures from Superset
  into a study report. That is a new use case, not part of this request, but the role and
  service-account plumbing chosen now should not preclude it.

**Recommendation:** ship the client-side download with phase 1 (it costs nothing), and
include the Celery/Playwright stack in the local compose from the start so that Alerts &
Reports and the screenshot API can be evaluated for print-quality output without a second
infrastructure change.

## 8. Local Stack Changes (umbrella `docker-compose.yml`)

| Service | Image | Purpose |
|---|---|---|
| `superset` | `elicitsoftware/superset:latest` (custom, from `apache/superset:6.1.0` + Authlib + Playwright/Chromium + Pillow) | Web app, port `8088:8088`, healthcheck `GET /health` |
| `superset-worker` | same | Celery worker for reports/screenshots/thumbnails |
| `superset-beat` | same | Celery beat for scheduled reports |
| `superset-init` | same, run-once | `superset db upgrade`, `superset init`, create `ElicitAnalyst` role, import shipped assets; depends on `survey` healthy |
| `redis` | `redis:7` | Celery broker and results cache |
| metadata DB | reuse the `db` container with a second database `superset` and owner role `superset` (created by an init SQL in the `elicit_db` image or by `superset-init`), or a separate `postgres` container | Superset's own tables; never the `survey` database |

Environment: `SUPERSET_SECRET_KEY`, `OIDC_CLIENT_SECRET`, `OIDC_SERVER_METADATA_URL`
(`host.docker.internal` form), `SUPERSET_REPORTING_DB_PASSWORD` (for
`surveyreport_user`; its password is set inside the `elicit_db` image and needs to be
surfaced the same way `SAOWNERPW`/`SURVEYPW` are), and the usual OTel variables if
Superset's OpenTelemetry instrumentation is wanted in Jaeger.

`buildDockerImages.sh` gains a Superset step; `status.sh` gains a probe of `:8088/health`;
the Prometheus scrape config would gain `superset:8088/metrics` only if that pillar is
ever wired in (it is not today).

## 9. Security and PHI

- `surveyreport_user` is read-only by construction; Superset's connection must be
  configured with **"Allow DML" off** and SQL Lab denied to the analytics role, so the
  only queries that run are the shipped datasets' SQL and chart aggregations.
- Respondent-level rows are keyed by `respondent_id`, which Admin can resolve to a person.
  Analytics users therefore see re-identifiable data even without names. Dataset column
  visibility should hide `fact_sections.name` and `other_cancer_name`, and row-level
  security by department (via a future `dim_department`) is the mechanism if some
  analysts must see only their own cohort.
- NFR-005 (PHI access auditing) is "Open" for Admin. Superset writes every dashboard and
  chart view to its `logs` table with user and timestamp; the `EVENT_LOGGER` hook can
  forward those to the same sink Admin adopts when NFR-005 is implemented.
- Superset is a large additional attack surface (Python web app, SQL Lab, file upload,
  Jinja templating in SQL). Keep `ENABLE_TEMPLATE_PROCESSING` off, `PUBLIC_ROLE_LIKE`
  unset, and place it behind the same perimeter as Admin.

### 9.1 Limiting an analyst to particular surveys or departments

Superset **Row Level Security** (RLS; feature flag `ROW_LEVEL_SECURITY`, on by default)
injects a SQL clause into the `WHERE` of every query a role runs against a dataset. Rules
are attached to one or more roles and one or more datasets, and the clause may use Jinja
(`{{ current_username() }}`) to look up the logged-in user. So the answer to "can
analytics limit what a user reports on" is **yes**: by survey with configuration alone,
and by department after two small Survey-side additions.

**By survey (works today).** Every `surveyreport` table and both proposed views carry
`survey_id`. A rule such as `survey_id IN (1, 3)` on a role, attached to every dataset,
restricts that role. Per-survey Superset roles (`ElicitAnalyst_Survey1`, ...) can be
mapped from Keycloak roles through `AUTH_ROLES_MAPPING` if the set of surveys is small
and static.

**By department (needs schema work).** Nothing in `surveyreport` knows about departments.
The link lives only in the `survey` schema, where `subjects` carries `department_id` and
`respondent_id`, and Admin users are mapped to departments through `user_departments`
(`user_id`, `department_id`) against `users` (`username`). Two Survey-owned additions
close the gap, in the same migration family as section 6.2.2:

| Addition | Purpose |
|---|---|
| `department_id` on the reporting side: a `dim_department(id, name, code)` plus a `department_key` on `fact_respondents` (maintained by the existing respondent triggers via `subjects`), and a `department_id` column on `answer_metadata_view` derived through `subjects.respondent_id` | Gives every fact row and every answer row a department to filter on |
| `surveyreport.analyst_departments` view: `SELECT u.username, ud.department_id FROM survey.users u JOIN survey.user_departments ud ON ud.user_id = u.id WHERE u.active`, granted to `${surveyreport_user}` | Exposes the mapping Admin already administers in its Edit User screen (UC-008/UC-016), so no second place to maintain it |

With those in place one **Base** rule (applies to everyone except roles explicitly
exempted, so a forgotten role is restricted rather than open) attached to every dataset
serves all analysts:

```sql
department_id IN (SELECT department_id
                  FROM surveyreport.analyst_departments
                  WHERE username = '{{ current_username() }}')
```

A user with no department mapping sees nothing, which is the safe default.

**Conditions and trade-offs.**

1. **Usernames must match.** The Superset username must equal the Admin username. With
   Keycloak login on both sides that holds only if Superset registers users from the
   same claim Admin uses (`preferred_username`); pin it in the custom security manager's
   `oauth_user_info()`.
2. **Jinja in RLS requires `ENABLE_TEMPLATE_PROCESSING`.** Section 9 recommends keeping
   it off. Turning it on is the price of a single dynamic rule, and makes denying SQL Lab
   to analysts mandatory. The alternative is static rules per department attached to
   per-department Superset roles mapped from Keycloak, which needs no templating but
   multiplies roles and moves department administration out of Admin.
3. **Every dataset needs the rule.** RLS is per dataset; one left out is unrestricted.
   Shipping the rules inside the exported assets (section 6.4) keeps them from drifting.
4. **Restriction stops at Superset's edge.** Downloaded images and CSVs contain whatever
   the viewer was allowed to see, and Alerts & Reports render as the report *owner*, not
   the recipient, so a scheduled report must be owned by someone whose restriction matches
   its audience.
5. **Department limits respondents, not the definition.** A restricted analyst still sees
   the full tag and question list; only the counts come from their department.
6. **Embedded mode is simpler.** Under Option B the guest token carries `rls` clauses
   directly, so Admin can supply `department_id IN (...)` from its own knowledge of the
   user without relying on username matching or templating.

## 10. Open Decisions

1. **Role name and location.** `elicit_analytics`, confirmed? Client role on
   `elicit-superset` (cleanest for Superset's mapping) or on `elicit-admin` (one place to
   administer, but then Superset must read `resource_access.elicit-admin.roles`)?
2. **Does `elicit_admin` imply `elicit_analytics`?** Proposal: no; grant explicitly.
3. **`DATABASE` authorization mode.** Accept that analytics is Keycloak-only, or extend
   `survey.user_roles` and `EditUserView` to hold more than one role per user?
4. **Where shipped Superset assets live.** Proposal: generic in the umbrella repo under
   `superset/`, FHHS-specific in the FHHS repo. Admin holds documentation only.
5. **Metadata database placement.** Second database in the existing `db` container
   (fewer moving parts) or a dedicated container (cleaner upgrade story)?
6. **`dim_survey` / survey titles.** Narrow grant on `survey.surveys`, or ETL-maintained
   dimension? (Survey repo change either way.)
7. **Include Celery/Playwright in phase 1?** Recommended yes for evaluation; it is the
   only path to print-quality images.
8. **Option B timing.** Is an embedded "Analytics home" inside Admin wanted at all, or is
   the SSO link sufficient?
9. **Free-text masking.** The answer metadata view replaces `TEXT`, `TEXTAREA`,
   `EMAIL` and `PASSWORD` answers with `(free text)`. Confirm that is the right PHI line,
   or whether those question types should be excluded from the view entirely.
10. **Default cohort for the tag charts.** Finished respondents only (proposed
    default filter), or every saved answer including in-progress respondents?
11. **Department restriction mechanism.** Dynamic RLS with Jinja and
    `ENABLE_TEMPLATE_PROCESSING` on (one rule, administered in Admin), or static
    per-department roles mapped from Keycloak (no templating, more roles)? See
    section 9.1.
12. **Untagged questions.** The metadata view only sees questions that carry a tag.
    Is that acceptable for analytics, or should FHHS (and future surveys) be required to
    tag every question an analyst may ask about?

## 11. Proposed Implementation Steps

| # | Step | Repo |
|---|---|---|
| 1 | Add `elicit-superset` client, `elicit_analytics` role, userinfo role mapper and `analyst` user to `keycloak/elicit-realm.json` | umbrella |
| 2 | Add `superset`, `superset-worker`, `superset-beat`, `superset-init`, `redis` services, a `superset/Dockerfile`, `superset_config.py`, and the metadata database bootstrap; extend `buildDockerImages.sh` and `status.sh` | umbrella |
| 3 | Surface the `surveyreport_user` password as an environment variable alongside the existing DB passwords | umbrella (`elicit_db` image) |
| 4 | `ElicitRoles.ANALYTICS`; `elicit.analytics.url` config; Analytics `SideNavItem` in `MainLayout`; role option in `EditUserView`; tests for menu visibility per role | Admin |
| 5 | AIUP artifacts: FR-020, UC-020, `Analyst` actor, requirement traceability | Admin |
| 6 | Add `surveyreport.answer_metadata_view` (section 6.2.2) on both migration tracks with the `${surveyreport_user}` grant; verify on a V3 database | Survey |
| 6a | Add `dim_department`, `fact_respondents.department_key`, `department_id` on the metadata view, and the `analyst_departments` view (section 9.1); ship the RLS rules with the dashboard assets | Survey, umbrella |
| 7 | Build the respondent funnel, status breakdown, and the two metadata-tag charts (sections 6.2.1 and 6.2.2) as the first charts of the Survey Operations dashboard locally, export, commit under `superset/assets/`, wire into `superset-init` | umbrella |
| 8 | Add the unpivoted family-history view as a Flyway migration; build and export the Family Health History dashboard | FHHS |
| 9 | Evaluate Alerts & Reports and the screenshot API for print output; record `WEBDRIVER_WINDOW` and format findings back in this document | umbrella |
| 10 | (Later) Option B embedded Analytics view with guest tokens | Admin |

## References

- Apache Superset home: <https://superset.apache.org/>
- Embedding dashboards and the embedded SDK: <https://superset.apache.org/docs/using-superset/embedding> and the `superset-embedded-sdk` README in the Superset repository
- Configuration (OAuth, `AUTH_ROLES_MAPPING`, Talisman/CSP): <https://superset.apache.org/docs/configuration/configuring-superset>
- Alerts & Reports: <https://superset.apache.org/docs/configuration/alerts-reports>
- Docker image customisation (Authlib, Playwright): <https://superset.apache.org/docs/installation/docker-builds>
- Survey star schema DDL: `Survey/src/main/resources/db/migration-v3/V002__Create_Reporting_Schema.sql`; runtime ETL SQL: `Survey/src/main/java/com/elicitsoftware/etl/Sql.java`
- FHHS fact view: `FHHS/src/main/resources/db/migration-v3/V0.0.3__CREATE_FHHS_FACT_VIEW.sql`
- Admin roles and navigation: `src/main/java/com/elicitsoftware/security/ElicitRoles.java`, `src/main/java/com/elicitsoftware/admin/flow/MainLayout.java`
- Keycloak realm: umbrella `keycloak/elicit-realm.json`
