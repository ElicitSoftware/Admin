# Superset Analytics — AIUP Implementation Plan

> **Status (2026-09-18):** Planned, not started. Derived from
> `docs/research/Superset.md` and the decisions recorded in section 1. Follows the AIUP
> order: vision → requirements → entity model → use-case diagram → use-case specs →
> implementation with UC-traceable tests.

## 1. Decisions (resolved with the product owner, 2026-09-18)

| # | Decision | Choice |
|---|---|---|
| D1 | Scope of this round | **Admin + local stack.** The Survey-owned `answer_metadata_view` and the metadata-tag chart (research §6.2.2) are deferred, as is anything else that changes the Survey repo. |
| D2 | Role name and home | `elicit_analytics`, a **client role on a new `elicit-superset` Keycloak client**. `elicit_admin` does **not** imply it; it is granted deliberately. |
| D3 | `DATABASE` authorization mode | **Extended.** `survey.user_roles` may hold an `elicit_analytics` row alongside the ladder role; the Edit User screen gains an Analytics checkbox. Superset itself still needs the Keycloak role. |
| D4 | Integration | **Options A and B together:** an embedded dashboard view inside Admin (guest tokens) *and* a link to full Superset with Keycloak SSO. |
| D5 | Superset infrastructure and assets | **Umbrella repo**, `superset/` directory beside `docker-compose.yml`. Admin holds docs, role, menu, view. |
| D6 | Local stack | Superset metadata database in the **existing `db` container** (second database `superset`); **Celery worker + beat + Redis + Playwright included now** so server-side image export can be evaluated. |
| D7 | AIUP artifacts | **Hand-added** in the style of FR-017–FR-019; `/use-case-spec` for the new use case. |
| D8 | Branches | Admin: `feature/analytics` off `V3`, PR into `V3`. Umbrella: `feature/superset` off `feature/author-service`, PR into that branch. |
| D9 | Row-level security | **None this round.** |
| D10 | Guest tokens | **Signed locally by Admin** with a shared HS256 secret (`GUEST_TOKEN_JWT_SECRET`). No Superset service account. |
| D11 | Edit User UI | Keep the single ladder ComboBox; **add an "Analytics" checkbox**. |
| D12 | Reporting DB password | Follows the owner-datasource pattern: username fixed in configuration, password from an environment variable (`SUPERSET_REPORTING_DB_PASSWORD`). The local value is the one the `elicitsoftware/elicit_db` image sets for `surveyreport_user` in its `V0.0.1__CREATE_USERS.sql`. |
| D13 | Menu | **One "Analytics" item** routing to the embedded view; that view carries an "Open in Superset" link (new tab). |
| D14 | Embedded content | The **Survey Operations** dashboard only (funnel, status breakdown, other §6.2 charts as built). |

## 2. Review of the research document against these decisions

Findings that change or sharpen what the research proposed:

- **Role visibility across clients.** Admin's Quarkus OIDC extension reads roles from
  `realm_access.roles` and `resource_access.<its own client>.roles` only. Keycloak, because
  the clients allow full scope, already puts *every* client role of the user under
  `resource_access` in Admin's tokens (verified 2026-09-18 with example tokens from a
  throwaway Keycloak), so no extra mapper is needed; what is needed is
  `quarkus.oidc.roles.role-claim-path` listing `realm_access/roles`,
  `resource_access/elicit-admin/roles` and `resource_access/elicit-superset/roles`, since
  setting the property replaces the defaults. Done in step 6.4.
- **`UserRoleService` is replace-all.** `setRole` deletes every row for the user before
  inserting, and `findRoleName` returns the first row. D3 requires separating the ladder
  grant from the analytics grant (UC-016 BR-055 changes). The DB check constraint
  `user_roles_role_name_ck` (`V0.0.10`) also rejects the new name and needs a migration.
- **`RoleSecurityIdentityAugmentor`** tests `ElicitRoles.ALL::contains` to decide whether
  an identity "has an Elicit role". Adding `ANALYTICS` to `ALL` means an analytics-only
  user is treated as role-bearing (no DB lookup in DATABASE mode). That is the intended
  reading; document it in UC-001.
- **No `@ConfigMapping`, `@NpmPackage`, or `@JsModule` exists in Admin today.** The
  research suggested `@ConfigMapping`; the plan uses the repo's `@ConfigProperty` pattern
  instead. The embedded SDK will be Admin's first hand-added npm dependency.
- **`quarkus-smallrye-jwt-build` is only transitively present.** Declare it explicitly
  before using `io.smallrye.jwt.build.Jwt`.
- **Guest-token claim shape.** Superset's `create_guest_access_token` emits `user`,
  `resources`, `rls_rules`, `iat`, `exp`, `aud`, `type: "guest"` (the REST body uses `rls`,
  the token uses `rls_rules`). Verify against the pinned 6.1.0 source before coding the
  signer.
- **Research §10** open decisions 1–8 and 11 are now closed by section 1; 9, 10 and 12
  stay open with the deferred Survey work. Update the research document's status header
  to point here.

## 3. AIUP artifacts (Admin, branch `feature/analytics`)

| Artifact | Change |
|---|---|
| `docs/vision.md` | Goals: add "Hand analysts off to the platform's analytics tool with single sign-on, and surface its primary dashboard inside the console." Scope: keep "cross-survey analytics dashboards → downstream reporting tools" out of scope for *building*, add "embedding and authorising access to that tool" in scope. Target users: add **Analyst** (researcher who reads aggregate dashboards; may hold no other console role). |
| `docs/requirements.md` | **FR-020 View Analytics Dashboard** — "As an analyst, I want an Analytics item that shows the Survey Operations dashboard inside the console and lets me open the full analytics tool, so that I can read and explore aggregate results without a second login." (High, Planned). **FR-021 Grant Analytics Access** — "As a system administrator, I want to grant or revoke a user's analytics access independently of their console role, so that researchers can read dashboards without gaining subject administration rights." (Medium, Planned). **NFR-011 Guest Token Lifetime** — embedded dashboard tokens expire within 5 minutes and are refreshed silently (Security, High). **C-012 Analytics Tool License** — the analytics tool is Apache Superset (Apache-2.0); Elicit redistributes only configuration and exported dashboard assets. Update the Traceability paragraph to FR-001–FR-021 ↔ UC-001–UC-021 (with FR-021 refining UC-016). |
| `docs/entity_model.md` | `USER_ROLE.roleName` validation rule: allowed values gain `elicit_analytics`; note that a user may hold one ladder role plus the analytics role. |
| `docs/use_cases.puml` | Actor `"Analyst" as analyst`. New `usecase "UC-020\nView Analytics Dashboard" as UC020` in the interactive-console group; `analyst --> UC001`, `analyst --> UC020`, `admin --> UC020` *only via grant* (no implicit arrow from admin; add a note). External actor `"Analytics Service" as analytics` → `UC020`. `oidc --> UC020` (SSO). |
| `docs/use_cases/UC-020-view-analytics-dashboard.md` | Written with `/use-case-spec UC-020`, then hand-edited. Main flow: analyst opens Analytics → console mints a guest token → embedded Survey Operations dashboard renders → "Open in Superset" opens the full tool in a new tab, Keycloak SSO completes without a prompt. Alternatives: A1 analytics URL not configured (item hidden, route 404); A2 user lacks `elicit_analytics` (item hidden, direct route → Access Restricted 403); A3 token expiry (SDK calls back, console mints a new token); A4 Superset unreachable (view shows an error panel with the direct link). Business rules: BR-080 analytics role is orthogonal, never implied; BR-081 guest tokens are read-only, dashboard-scoped, ≤ 5 min; BR-082 the embedded dashboard id and Superset URL are deployment configuration, never code. |
| `docs/use_cases/UC-016-manage-user-role-assignments.md` | Revise: step 2 gains the Analytics checkbox; step 5 "sets, replaces, or clears the ladder grant and sets or clears the analytics grant". BR-054 list gains `elicit_analytics`; BR-055 becomes "at most one **ladder** grant plus at most one analytics grant"; BR-056 unchanged for the ladder. Status stays Implemented with a "revised 2026-09-18" note. |
| `docs/use_cases/UC-001-...md` | Add a sentence to the role-resolution rules: `elicit_analytics` passes through expansion unchanged and counts as an Elicit role for the OIDC/DATABASE decision. |
| `docs/research/Superset.md` | Status header: "Implementation planned, see `docs/plan/superset-analytics-implementation.md`"; §10 marks decisions 1–8 and 11 resolved. |

## 4. Admin implementation (branch `feature/analytics`)

### 4.1 Roles and persistence (UC-016, UC-001)

1. `security/ElicitRoles.java`: `ANALYTICS = "elicit_analytics"`; add to `ALL`; **not** in
   `IMPLIED`. Add `LADDER = Set.of(ADMIN, USER, IMPORTER)` for the service below.
2. Migration `src/main/resources/db/migration/V0.0.19__Allow_Analytics_Role.sql`: drop and
   re-add `user_roles_role_name_ck` with the four names.
3. `service/UserRoleService.java`: split the single grant into two independent grants.
   `setLadderRole(userId, role)` deletes only rows whose name is in `LADDER` then inserts;
   `clearLadderRole(userId)` likewise; `findLadderRole(userId)`; new
   `setAnalytics(userId, boolean)` and `hasAnalytics(userId)`. Keep `setRole`/`clearRole`/
   `findRoleName` as thin deprecated delegates only if other callers exist (the explore found
   none besides `EditUserView`); otherwise rename outright and fix the tests.
4. `admin/flow/EditUserView.java`: add `Checkbox analyticsBox = new Checkbox("Analytics")`
   to `roleSection` under the ComboBox, with a one-line `Paragraph` ("Grants read access to
   the analytics dashboards; independent of the role above"). Load in `beforeEnter`
   (DATABASE mode) via `hasAnalytics`; save after `userService.save(user)` via
   `setAnalytics`.
5. `security/RoleSecurityIdentityAugmentor.java`: no code change expected; add tests
   proving an analytics-only identity is left alone in OIDC mode and merged with DB rows in
   DATABASE mode.
6. `application.properties`: `quarkus.oidc.roles.role-claim-path=realm_access/roles,resource_access/elicit-admin/roles,resource_access/elicit-superset/roles`
   (`realm_access/roles` kept because the `etl` user carries `elicit_importer` as a realm
   role). Done: Admin starts with it and the token check in step 6.4 passed.

### 4.2 Configuration

`admin/flow/AnalyticsConfig.java` (`@ApplicationScoped`, `@ConfigProperty` fields, same
shape as `AuthorizationModeConfig`):

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `elicit.analytics.superset-url` | `SUPERSET_URL` | *(empty → feature off)* | Browser-reachable Superset base URL (`http://localhost:8088` locally) |
| `elicit.analytics.dashboard-id` | `SUPERSET_DASHBOARD_ID` | *(empty)* | Embedded dashboard uuid from Superset's "Embed dashboard" dialog |
| `elicit.analytics.guest-token-secret` | `SUPERSET_GUEST_TOKEN_SECRET` | *(empty)* | HS256 secret shared with Superset's `GUEST_TOKEN_JWT_SECRET` |
| `elicit.analytics.guest-token-ttl-seconds` | — | `300` | Token lifetime (NFR-011) |
| `elicit.analytics.guest-token-audience` | — | *(empty)* | Mirrors Superset's `GUEST_TOKEN_JWT_AUDIENCE` when set |

`isEnabled()` = url, dashboard id and secret all present. `DebugView` masks the secret
using its existing `maskToken` convention.

### 4.3 Guest token minting (UC-020 BR-081)

- `pom.xml`: add `io.quarkus:quarkus-smallrye-jwt-build` explicitly.
- `service/SupersetGuestTokenService.java`: `String mint(SecurityIdentity identity)` builds
  the claim set with `io.smallrye.jwt.build.Jwt.claims()` — `user` (username, first/last
  from the session `User`), `resources: [{type: "dashboard", id: <dashboard-id>}]`,
  `rls_rules: []`, `iat`, `exp = iat + ttl`, `type: "guest"`, `aud` when configured — and
  signs with `.jws().algorithm(HS256).signWithSecret(secret)`.
- `rest/AnalyticsResource.java`: `GET /api/analytics/guest-token` → `{"token": "..."}`;
  `@RolesAllowed("elicit_analytics")`, `@Produces(APPLICATION_JSON)`, no caching headers.
  Check `quarkus.http.auth.permission.*` so this path stays under the `authenticated`
  catch-all (the OIDC `hybrid` application type accepts the session cookie).

### 4.4 The Analytics view and menu (UC-020)

- Frontend: `src/main/frontend/analytics/superset-embed.js` (Vaadin 25 frontend folder,
  created new) importing `embedDashboard` from `@superset-ui/embedded-sdk` and exposing
  `window.elicitAnalytics.embed(mountEl, {id, supersetDomain, tokenUrl})` whose
  `fetchGuestToken` does `fetch(tokenUrl, {credentials: "same-origin"})`; sets
  `dashboardUiConfig: {hideTitle: true, hideChartControls: false, filters: {expanded: true}}`
  and sizes the iframe to the mount.
- `admin/flow/AnalyticsView.java`: `@Route(value = "analytics", layout = MainLayout.class)`,
  `@RolesAllowed("elicit_analytics")`, `@NpmPackage(value = "@superset-ui/embedded-sdk", version = "<pinned>")`,
  `@JsModule("./analytics/superset-embed.js")`. Layout: `H2("Analytics")`, an
  `Anchor(supersetUrl, "Open in Superset")` with `setTarget("_blank")` and a
  `VaadinIcon.EXTERNAL_LINK` suffix, a full-height `Div` mount with a stable id; on
  `onAttach`, `executeJs("window.elicitAnalytics.embed($0, $1)", mount, configJson)`. If
  `!analyticsConfig.isEnabled()` the view renders an "Analytics is not configured for this
  deployment" notice instead (A1).
- `admin/flow/MainLayout.createNavBar()`: after Register Subjects and before the Admin
  section, `if (identity.hasRole(ElicitRoles.ANALYTICS) && analyticsConfig.isEnabled())`
  add `new SideNavItem("Analytics", AnalyticsView.class, VaadinIcon.CHART.create())`.
  Update the class Javadoc's menu inventory.

### 4.5 Tests (all UC-traceable, `QuarkusBrowserlessTest` pattern)

| Test class | Cases |
|---|---|
| `security/ElicitRolesTest` | UC-001/BR-080: `analyticsDoesNotExpand`, `adminDoesNotImplyAnalytics`, `allContainsAnalytics` |
| `service/UserRoleServiceTest` | UC-016/BR-055: `settingLadderRolePreservesAnalytics`, `setAnalyticsAddsRow`, `clearingAnalyticsKeepsLadder`, `checkConstraintAcceptsAnalytics` |
| `admin/flow/EditUserViewRoleAssignmentTest` | UC-016: `analyticsCheckboxVisibleInDatabaseMode`, `savingPersistsAnalyticsWithRole`, `uncheckingRemovesOnlyAnalyticsRow` |
| `admin/flow/EditUserViewTest` | UC-016/A1: `analyticsCheckboxHiddenInOidcMode` |
| `security/RoleSecurityIdentityAugmentor{Oidc,Database}ModeTest` | UC-001: analytics-only OIDC identity is not sent to the DB; DB analytics row merges with OIDC ladder role |
| `admin/flow/MainLayoutTest` | UC-020: `analystSeesAnalyticsItem`, `userWithoutAnalyticsDoesNotSeeItem`, `analyticsItemHiddenWhenNotConfigured` (test profile with the URL unset) |
| `admin/flow/AnalyticsViewTest` | UC-020: renders "Open in Superset" with the configured URL and target `_blank`; renders the not-configured notice under the unset profile |
| `service/SupersetGuestTokenServiceTest` | UC-020/BR-081: token verifies with the shared secret; claims `type=guest`, `resources[0].id`, `exp − iat = ttl`; audience present only when configured |
| `rest/AnalyticsResourceTest` | UC-020: 200 with `token` for `elicit_analytics`; 403 for `elicit_user`; 401 anonymous |

New test profile `test/AnalyticsDisabledTestProfile` (unset URL) and analytics values in
`%test` properties. Run via the Quarkus Dev MCP test runner, never `mvn` directly; do not
`mvn clean` while dev mode runs.

## 5. Local stack implementation (umbrella, branch `feature/superset`)

### 5.1 Keycloak (`keycloak/elicit-realm.json`, edited via script, then re-exported)

- Client `elicit-superset`: confidential, `secret`, `standardFlowEnabled`, redirect
  `http://localhost:8088/*`, web origin `http://localhost:8088`, default scopes as
  `elicit-author`; protocol mapper "elicit-superset client roles" (`oidc-usermodel-client-role-mapper`,
  claim `resource_access.${client_id}.roles`, userinfo + id + access token).
- Client roles on `elicit-superset`: `elicit_analytics`, `elicit_superset_admin`.
- No mapper on `elicit-admin` is needed: with full scope allowed, the default `roles`
  client scope already emits the user's `elicit-superset` roles in Admin's tokens (§2).
- Users: new `analyst` / `analyst` with `elicit_analytics` only; `admin` gains
  `elicit_analytics` and `elicit_superset_admin` so the existing local login can author
  dashboards.

### 5.2 `superset/` directory

| File | Content |
|---|---|
| `Dockerfile` | `FROM apache/superset:6.1.0`; add `Authlib`, `Pillow`, `playwright` + `playwright install chromium` (+ deps); copy config and assets; `USER superset` |
| `superset_config.py` | `SECRET_KEY`, metadata `SQLALCHEMY_DATABASE_URI` (env), Redis cache/Celery (`CeleryConfig`, beat schedule for reports), `FEATURE_FLAGS = {EMBEDDED_SUPERSET, ALERT_REPORTS, THUMBNAILS, PLAYWRIGHT_REPORTS_AND_THUMBNAILS}`, `WEBDRIVER_TYPE = "playwright"`, `WEBDRIVER_WINDOW`, `GUEST_TOKEN_JWT_SECRET` (env), `GUEST_TOKEN_JWT_EXP_SECONDS = 300`, `GUEST_ROLE_NAME = "ElicitGuest"`, `TALISMAN_CONFIG` with `frame-ancestors` `http://localhost:8081`, OAuth block from research §4.5 with `CUSTOM_SECURITY_MANAGER`, `THEME_DEFAULT` brand tokens, `ENABLE_TEMPLATE_PROCESSING = False`, `SQLLAB` denied to analysts via role |
| `elicit_security.py` | `ElicitSecurityManager(SupersetSecurityManager)`: `oauth_user_info` reads userinfo, returns `username = preferred_username`, names, email, `role_keys = resource_access["elicit-superset"]["roles"]` |
| `bootstrap.py` | Run under `superset shell`: create roles `ElicitAnalyst` (Gamma minus SQL Lab, plus `can_read` on Dashboard/Chart and `datasource access` on the reporting datasets) and `ElicitGuest` (dashboard read only); idempotent |
| `init.sh` | `superset db upgrade`, `superset init`, `python bootstrap.py`, create the `superset` metadata DB if missing (via `elicit_owner`), then `superset import-dashboards -p assets/survey-operations.zip -u admin` when the file exists |
| `assets/` | `survey-operations.zip` exported from the locally authored dashboard (committed after step 6.6); `databases/` connection uses `postgresql://surveyreport_user:${SUPERSET_REPORTING_DB_PASSWORD}@db:5432/survey` patched by `init.sh` |
| `README.md` | What runs, ports, how to author/export/re-import, how to obtain the embedded dashboard uuid |

### 5.3 `docker-compose.yml`

Services `redis` (`redis:7`, no published port), `superset` (`elicitsoftware/superset:latest`,
`8088:8088`, healthcheck `curl -f http://localhost:8088/health`, `depends_on: db healthy,
redis`), `superset-worker`, `superset-beat`, `superset-init` (run-once, `depends_on: survey
healthy`). Shared env: `SUPERSET_SECRET_KEY`, `SUPERSET_GUEST_TOKEN_SECRET`,
`OIDC_CLIENT_SECRET: secret`, `OIDC_SERVER_METADATA_URL:
http://host.docker.internal:8180/realms/elicit/.well-known/openid-configuration`,
`SUPERSET_DB_URI`, `SUPERSET_REPORTING_DB_PASSWORD`, `ELICIT_OWNER_DB_PASSWORD` (init only).
Admin service gains `SUPERSET_URL: http://localhost:8088`, `SUPERSET_DASHBOARD_ID`,
`SUPERSET_GUEST_TOKEN_SECRET` (same value as Superset's).

### 5.4 Scripts and docs

`buildDockerImages.sh` gains a Superset step after Author; `status.sh` adds
`8088:Superset Analytics`; `CLAUDE.md` port table adds 8088 and a note that `superset-init`
is expected to exit; `README.md` mentions Analytics.

## 6. Sequenced steps

| # | Step | Repo | Depends on |
|---|---|---|---|
| 6.1 | Branches: `feature/analytics` (Admin, off `V3`), `feature/superset` (umbrella, off `feature/author-service`) | both | — |
| 6.2 | AIUP artifacts (section 3), including `/use-case-spec UC-020` | Admin | — |
| 6.3 | Roles, migration, `UserRoleService`, `EditUserView`, tests (§4.1, §4.5 rows 1–5) | Admin | 6.2 |
| 6.4 | Keycloak realm changes (§5.1); verify `admin` token carries both `resource_access` entries; set `role-claim-path` — **done 2026-09-18** (umbrella `6a676b1`) | umbrella, Admin | 6.3 |
| 6.5 | Superset image, config, compose, scripts (§5.2–5.4); bring the stack up; log in to Superset as `admin` via Keycloak | umbrella | 6.4 |
| 6.6 | Author the Survey Operations dashboard in local Superset (funnel + status breakdown from research §6.2.1, then the rest of §6.2); enable embedding with allowed domain `localhost:8081`; export ZIP; commit under `superset/assets/`; re-run `superset-init` to prove the import | umbrella | 6.5 |
| 6.7 | `AnalyticsConfig`, guest-token service, REST endpoint, tests (§4.2, §4.3, §4.5 rows 8–9) | Admin | 6.3 |
| 6.8 | `AnalyticsView`, npm dependency, JS module, `MainLayout` item, tests (§4.4, §4.5 rows 6–7) | Admin | 6.6, 6.7 |
| 6.9 | End-to-end verification (section 7); update research doc status; PRs per D8 | both | all |

## 7. Verification

1. **Admin test suite** green via the Quarkus Dev MCP runner (`devui-testing_runTests`); the
   new tests in §4.5 all present and traceable.
2. **Stack:** `docker compose up -d`, the three-pass init, then `superset-init` exits 0 and
   `status.sh` shows Superset on 8088.
3. **SSO:** log in to `http://localhost:8088` as `analyst`; Keycloak completes without a
   Superset login form; the user lands with role `ElicitAnalyst` and sees only the Survey
   Operations dashboard; SQL Lab is absent.
4. **Menu and access:** as `analyst`, Admin shows the Analytics item; as `user`, it does
   not, and `http://localhost:8081/analytics` returns the Access Restricted view (403);
   with `SUPERSET_URL` unset the item is absent for `analyst`.
5. **Embedded view:** the dashboard renders inside Admin; the browser console shows no
   `frame-ancestors` or CORS error; leaving the tab open past 5 minutes triggers a
   successful token refresh (network tab shows a second `guest-token` call); "Open in
   Superset" opens the full tool in a new tab with no prompt.
6. **Edit User (DATABASE mode):** `elicit.authorization.mode: DATABASE`, grant Analytics to
   `user` via the checkbox; `survey.user_roles` shows two rows; the menu item appears for
   `user` on next login; unchecking removes only the analytics row.
7. **Image export:** from a chart's menu, "Download as image" produces a PNG; from Alerts &
   Reports, a scheduled report to Mailpit arrives with a PNG attachment (Playwright path).
8. **Funnel numbers** match research §6.2.1 (Registered 6, Started 1, Finished 0 on the
   local data).

## 8. Out of scope (deferred, tracked in the research document)

- `surveyreport.answer_metadata_view` and the metadata-tag chart (§6.2.2).
- Row-level security by survey or department (§9.1).
- `dim_survey` / survey titles, `dim_date` beyond 2029, the `survey_id = 1` trigger fix.
- Family Health History dashboard (FHHS repo).
