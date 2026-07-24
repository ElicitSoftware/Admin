# Quarkus + Vaadin 25 Code Review — Elicit Admin

**Date:** 2026-07-24
**Scope:** Server-side Vaadin Flow views in `com.elicitsoftware.admin.flow` and their
supporting data-access / persistence classes, reviewed against the bundled Quarkus/Vaadin 25
skills (`data-providers`, `forms-and-validation`, and the Flow best practices they encode).
Findings span both the Vaadin UI layer and the Quarkus/Panache backend (query
parameterization, paging, transactions, logging).
**Stack:** Quarkus 3.37.3, Vaadin 25.2.3, Hibernate ORM with Panache, PostgreSQL.

> Not yet run: the `quarkus_update` / `quarkus_skills` MCP workflow (requires the app
> running in dev mode). Pull extension-specific skills before implementing if possible.

## What's already good

- `PaginationControls` is a clean, reusable component with proper ARIA labels and
  `LumoUtility` styling — the model to follow elsewhere.
- `EditDepartmentView` uses `Binder` correctly (required indicators, `StringLengthValidator`,
  `EmailValidator`, status-change-driven save button).
- Correct core components throughout: `AppLayout`, `SideNav`, `Grid`, `MultiSelectComboBox`.

## Findings & remediation plan

Ordered by priority. Check off as completed.

### 🔴 Critical

- [ ] **1. HQL injection in `SearchView.getStatusSQL()`** — `SearchView.java:662-693`
  User input from every filter field (`token`, `firstName`, `lastName`, `email`, `phone`)
  is concatenated directly into the query string and executed via
  `StatusDataSource.fetch(sql, ...)`. A value like `') OR ('1'='1` alters the query.
  `StatusDataSource`'s own Javadoc warns to parameterize, but the caller ignores it.
  **Fix:** use Panache named parameters (e.g. `Status.stream("token like ?1", "%"+token+"%")`)
  and pass a parameter map through `StatusDataSource` instead of a pre-built query string.

### 🟠 Significant

- [ ] **2. Leaked `ScheduledExecutorService`** — `SearchView.java:137, 603`
  Each `SearchView` creates `Executors.newScheduledThreadPool(1)` and schedules a 10s
  refresh with no `onDetach`/shutdown. Every navigation spawns a pool that never dies and
  keeps calling `ui.access()` on a detached UI.
  **Fix:** shut down the scheduler in `onDetach`, or replace with `ui.setPollInterval(10000)`.
  Confirm `@Push` is configured (or use polling) — `ui.access()` won't reach the browser
  between requests otherwise.

- [ ] **3. Entities lack `equals`/`hashCode`** — `Status`, `User`, `Department`
  All extend `PanacheEntityBase` with no override. The data-providers skill requires
  ID-based identity for correct `Grid` selection tracking and `refreshItem`. The
  "All Departments" `id == -1` sentinel hack in `SearchView` is a symptom.
  **Fix:** add ID-based `equals`/`hashCode` to entities used as Grid/ComboBox items.

- [ ] **4. In-memory pagination in `StatusDataSource`** — `StatusDataSource.java:160, 222`
  `Status.stream(sql).skip(offset).limit(limit)` and `Status.stream(sql).count()` stream
  the entire result set from the DB, then page/count in Java. The 10s auto-refresh
  multiplies the cost.
  **Fix:** use Panache DB-level paging — `Status.find(query, params).page(pageIndex, pageSize).list()`
  and `.count()`.

### 🟡 Moderate

- [ ] **5. Eager `setItems(listAll())` in grids** — `UsersView.java:186`, `DepartmentsView.java:84`
  Loads all rows. Acceptable for small admin tables today; add a comment noting the ceiling
  and switch to lazy loading if these grow past ~1000 rows.

- [ ] **6. `EditUserView` doesn't use `Binder`** — `EditUserView.java:184-190`
  Manually reads each field into the entity, with no validation on username/name.
  **Fix:** convert to `Binder` to match `EditDepartmentView`.

- [ ] **7. Persistence logic inside views** — `EditUserView.saveUser`, `EditDepartmentView.saveDepartment`
  `@Transactional` persistence + `entityManager.merge` run in the Vaadin view.
  **Fix:** move data access into a service, keeping views focused on UI.

- [ ] **8. Raw `innerHTML` injection (XSS)** — `SearchView.java:278`, `RegisterView.java:654`
  `setProperty("innerHTML", ... + principalName + ...)` interpolates untrusted data and
  bypasses the component API.
  **Fix:** use `Span`/`Html`/text nodes instead of `innerHTML`.

### 🟢 Minor / polish

- [ ] **9. Inline styles** — replace `getStyle().set(...)` (margins, colors, `white-space`)
  with `LumoUtility` classes or CSS, following the `PaginationControls` pattern.
- [ ] **10. `System.out.println` auth logging** — `UiSessionLogin.java:124-138`
  Use a proper logger; note it dumps all active usernames to stdout on failed lookup (info leak).
- [~] **11. No tests** — CLAUDE.md requires tests traceable to `UC-XXX`.
  *In progress:* a JUnit 5 suite now exists — unit tests (validators, token
  generator, DTOs), `@QuarkusTest` DB/security tests (Testcontainers + mock
  OIDC), and browserless UI tests (`PaginationControlsTest`,
  `UnauthorizedViewTest`) for the dependency-free views. The data-heavy views
  (`SearchView`, `RegisterView`, the edit views) still need coverage and are
  best tackled alongside findings #1–#8, which make them testable.
- [ ] **12. Duplicated sort-column mapping** — `SearchView` string `switch` can drift from
  `setSortProperty`. Use column-name constants on the entity.

## Summary

| Priority | Count | Headline |
|---|---|---|
| 🔴 Critical | 1 | HQL injection in SearchView filters |
| 🟠 Significant | 4 | Thread-pool leak, missing equals/hashCode, in-memory pagination, missing Binder |
| 🟡 Moderate | 4 | innerHTML XSS, persistence in views, eager grid loads, EditUserView |
| 🟢 Minor | 4 | inline styles, logging, no tests, sort-column constants |

**Fix first regardless of sequencing:** #1 (injection) and #2 (thread leak) — both are live
security/correctness bugs, not style.
