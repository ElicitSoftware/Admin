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

**Decision**: Promote both methods to package-private (remove the `private` modifier, leave no access modifier). Test classes in `com.elicitsoftware.service` can then call them directly. This is a minimal, reversible change that doesn't affect any callers outside the package (there are none).

**Rationale**: Alternatives considered:
- *Test through round-trip only* — rejected; a round-trip failure for an escape bug would require DB setup, making the feedback loop slow.
- *Reflection* — rejected; brittle, verbose, and bypasses compiler checks.
- *Extract to a separate `EscapeUtils` class* — over-engineering for two methods; the constitution discourages unnecessary abstractions.

---

## Finding 3 — Playwright + Browserless Integration

**Question**: Which Java Playwright version and browserless image should be used, and how is the WebSocket connection established?

**Decision**:
- **Library**: `com.microsoft.playwright:playwright:1.49.0` (latest stable as of May 2026, compatible with Java 21).
- **Image**: `ghcr.io/browserless/chromium:latest` — exposes a Playwright-compatible WebSocket server on port `3000`. The endpoint pattern is `ws://<host>:<port>`.
- **Connection**: Use `playwright.chromium().connect(wsEndpoint)` (not `connectOverCDP`) — browserless's chromium image supports the Playwright protocol directly.
- **Testcontainers**: `GenericContainer<>("ghcr.io/browserless/chromium:latest").withExposedPorts(3000)` in a `@BeforeAll` static block of a `@QuarkusIntegrationTest` class.

**Configuration required**: Add to `pom.xml` test scope:
```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.49.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

`org.testcontainers:testcontainers` version is managed by the Quarkus BOM (3.34.1 includes Testcontainers 1.20.x).

**Alternatives considered**:
- *Selenium + ChromeDriver* — rejected; requires a separate ChromeDriver binary and is more complex to configure with a remote browser.
- *Karibu-Testing for smoke test* — rejected (see Complexity Tracking in plan.md); Karibu cannot test HTTP-level rendering or JS bundle initialisation.

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
| III — Browserless UI Testing | ⚠️ JUSTIFIED DEVIATION | `UiSmokeTest` uses Playwright; individual view tests use Karibu-Testing (separate follow-on) |
| IV — 70% Coverage Gate | ✅ PASS | `jacoco-maven-plugin` enforces 0.70 at `verify` phase |
| V — Observability | N/A | No new state-changing production operations |
