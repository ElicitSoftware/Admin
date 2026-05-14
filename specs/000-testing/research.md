# Research: Test Coverage for Export/Import Services

**Feature**: `000-testing` | **Date**: 2026-05-13

---

## Finding 1 — Survey Schema Dependency

**Question**: How do we run `@QuarkusTest` integration tests when the Admin service's Flyway migrations have FK references to tables it does not own?

**Discovery**: Inspecting `V0.0.1__CREATE_ADMIN_SCHEMA.sql` reveals that the Admin schema creates FKs to `survey.surveys(id)` and `survey.respondents(id)`:
- `survey.subjects.survey_id → survey.surveys(id)`
- `survey.subjects.respondent_id → survey.respondents(id)`
- `survey.user_surveys.survey_id → survey.surveys(id)`

These tables are owned by the Elicit Survey service (a separate application) and are not created by any Admin Flyway migration. Without them, Admin Flyway migrations fail with "relation does not exist."

**Decision**: Create `src/test/resources/db/test/V0.0.0__CREATE_SURVEY_SCHEMA_STUBS.sql`. Flyway version `0.0.0` sorts before `0.0.1`, so stubs run first. The file creates the minimal survey structure tables (surveys, respondents, steps, sections, questions, select_groups, select_items, steps_sections, sections_questions, answers, dependents, question_types, operators, actions) with all columns used by the export/import services. Static lookup rows (question_types, operators, actions) are also seeded here.

**Configuration required**: Add to `application.properties`:
```properties
%test.quarkus.flyway.owner.locations=db/migration,db/test
%test.quarkus.flyway.owner.schemas=survey
```

Quarkus Dev Services starts a fresh PostgreSQL container for `@QuarkusTest`; the `db/test` stubs ensure the container is fully initialised before any test runs.

**Alternatives considered**:
- *Disable FK constraints in tests* — rejected; FK integrity checking is a core goal (SC-002).
- *Separate test datasource without migrations* — rejected; defeats the purpose of running real Flyway migrations in the test environment.
- *Mock `EntityManager`* — rejected; the services use native SQL, making mocking brittle and inaccurate.

---

## Finding 2 — Helper Method Visibility

**Question**: `SurveyDefinitionExportService.escapeField()` and `SurveyDefinitionImportService.parseFields()` are `private`. How do we unit-test them directly without a running database?

**Discovery**: Code inspection confirms both methods are `private`. The spec requires pure unit tests (FR-007: no DB) for escape/unescape symmetry.

**Decision**: Promote both methods to `static` package-private (remove the `private` modifier and add `static`). Both `escapeField` and `parseFields` are pure string transformation functions with no instance state — `escapeField` does four `String.replace` calls and `parseFields` does character-by-character parsing, neither touching `EntityManager`. Making them `static` allows `EscapeUnescapeTest` to call `SurveyDefinitionExportService.escapeField(value)` and `SurveyDefinitionImportService.parseFields(data)` directly from a plain JUnit 5 class with no CDI container, no `@QuarkusTest`, and no Quarkus startup overhead. This is a minimal, reversible change — there are no callers outside the class, and the `static` keyword is strictly more expressive than package-private instance access for a stateless utility function.

**Rationale**: Alternatives considered:
- *Test through round-trip only* — rejected; a round-trip failure for an escape bug would require DB setup, making the feedback loop slow.
- *Reflection* — rejected; brittle, verbose, and bypasses compiler checks.
- *Extract to a separate `EscapeUtils` class* — over-engineering for two methods; the constitution discourages unnecessary abstractions.

---

## Finding 3 — Karibu-Testing Integration

**Question**: Which Karibu-Testing version supports Vaadin 25, and how is the simulated Vaadin environment configured in a `@QuarkusTest` class?

**Decision**:
- **Library**: `com.github.mvysny.kaributesting:karibu-testing-v24:2.2.0` — the `v24` artifact supports Vaadin 24 and above, including Vaadin 25.
- **Setup**: Annotate the test class with `@QuarkusTest`. In `@BeforeEach`, call `MockVaadin.setup()` (no-arg Java form — do **not** pass `new Routes().autoDiscoverViews(...)`, which is a Kotlin-idiomatic overload). When running under `@QuarkusTest`, the Quarkus Vaadin extension initialises the route registry as part of CDI container startup, so MockVaadin's no-arg setup picks up all `@Route`-annotated views automatically. In `@AfterEach`, call `MockVaadin.teardown()` (note: lowercase `d`).
- **Navigation**: Use `UI.getCurrent().navigate("route")` to trigger Vaadin's router, then `_get(ComponentClass.class)` to assert the component is present in the component tree.
- **Dev Services**: All Karibu tests run as `@QuarkusTest`, sharing the **single** Dev Services PostgreSQL container already started for the round-trip tests. No additional Docker container or `@QuarkusIntegrationTest` is needed.

**Configuration required**: Add to `pom.xml` test scope:
```xml
<dependency>
    <groupId>com.github.mvysny.kaributesting</groupId>
    <artifactId>karibu-testing-v24</artifactId>
    <version>2.2.0</version>
    <scope>test</scope>
</dependency>
```

Version is not managed by the Quarkus BOM; pin explicitly.

**Alternatives considered**:
- *Playwright + browserless* — rejected by user preference; avoids extra Docker container overhead and `@QuarkusIntegrationTest` complexity.
- *Selenium + ChromeDriver* — rejected; requires a separate ChromeDriver binary and a real browser.

---

## Finding 4 — Test Transaction Isolation

**Question**: How are test fixtures inserted and rolled back so that tests are isolated and repeatable (FR-009)?

**Decision**: Use `@TestTransaction` on each `@QuarkusTest` integration test method. Quarkus wraps the test method in a transaction and rolls it back after the method completes — no manual cleanup SQL is needed. The `@QuarkusIntegrationTest` smoke test does not insert any fixtures (it only navigates the UI), so no rollback strategy is needed there.

**Rationale**: `@TestTransaction` is the idiomatic Quarkus approach for DB test isolation. It works with Dev Services and avoids test-order dependencies.

---

## Finding 5 — OIDC in the Test Profile

**Question**: The application enforces OIDC authentication (`quarkus.http.auth.permission.authenticated.policy=authenticated`). How do `@QuarkusIntegrationTest` smoke tests reach the UI without credentials?

**Decision**: Add to `application.properties`:
```properties
%test.quarkus.oidc.enabled=false
%test.quarkus.http.auth.permission.authenticated.policy=permit
```

This disables OIDC for the `%test` profile and permits all requests, so the browser can reach the Vaadin shell without a Keycloak redirect. The smoke test verifies rendering, not authentication.

**Alternatives considered**:
- *Provide a test Keycloak container* — over-engineering; auth is not a goal of this spec.
- *Use a mock OIDC server (`quarkus-test-oidc-server`)* — adds complexity and a new dependency for no additional value in a rendering smoke test.

---

## Post-Design Constitution Re-Check (Phase 1)

| Principle | Status | Notes |
|-----------|--------|-------|
| I — Specification-First | ✅ PASS | Design follows spec; no scope creep |
| II — Test-First (TDD) | ✅ PASS | All test classes written before production helper changes |
| III — Browserless UI Testing | ✅ PASS | UI tests use Karibu-Testing (MockVaadin) as required; single Dev Services PostgreSQL shared by all `@QuarkusTest` classes |
| IV — 70% Coverage Gate | ✅ PASS | `jacoco-maven-plugin` enforces 0.70 at `verify` phase |
| V — Observability | N/A | No new state-changing production operations |
