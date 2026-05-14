# Quickstart: Running the Test Suite

**Feature**: `000-testing` | **Date**: 2026-05-13

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` (or use `./mvnw`) |
| Docker | 24+ | Required for Quarkus Dev Services (PostgreSQL) and the browserless container |

Docker must be running before executing any test that touches the database or the UI smoke test.

---

## Running All Tests

```bash
./mvnw verify
```

This runs:
1. **Unit tests** (Surefire, `*Test.java`): `EscapeUnescapeTest`, `ExportFormatContractTest` — no Docker required for these two classes.
2. **Integration and UI tests** (Surefire, `@QuarkusTest`): `SurveyDefinitionRoundTripTest`, `RespondentRoundTripTest`, `AdminViewTest` — Quarkus Dev Services starts **a single** PostgreSQL 17 container automatically; Karibu-Testing drives the Vaadin UI in-process with no additional Docker container.
3. **JaCoCo coverage check** — enforces ≥ 70% line coverage; build fails if threshold is not met.

Coverage report is written to `target/site/jacoco/index.html`.

---

## Running Specific Test Categories

### Unit tests only (no Docker)
```bash
./mvnw test -pl . -Dtest="EscapeUnescapeTest,ExportFormatContractTest"
```

### Database integration tests only
```bash
./mvnw test -pl . -Dtest="SurveyDefinitionRoundTripTest,RespondentRoundTripTest"
```

### UI tests only (Karibu, no browser)
```bash
./mvnw test -pl . -Dtest="AdminViewTest"
```

### Skip integration tests (fast unit-only build)
```bash
./mvnw test -DskipITs
```

---

## Test Configuration (`application.properties` additions)

The following `%test` profile entries must be present in `src/main/resources/application.properties`:

```properties
# Test profile — disable OIDC so UI smoke test can reach the app without credentials
%test.quarkus.oidc.enabled=false
%test.quarkus.http.auth.permission.authenticated.policy=permit

# Point Flyway at both admin migrations and the survey schema stubs
%test.quarkus.flyway.owner.locations=db/migration,db/test
%test.quarkus.flyway.owner.schemas=survey

# Dev Services provides a PostgreSQL 17 container automatically — no JDBC URL needed
# quarkus.datasource.db-kind=postgresql is already set in the base profile
```

---

## Test Database Setup (automatic)

Quarkus Dev Services starts **a single** fresh PostgreSQL 17 container shared across all `@QuarkusTest` classes (`SurveyDefinitionRoundTripTest`, `RespondentRoundTripTest`, `AdminViewTest`). No additional container is started for the Karibu UI tests. Flyway runs two migration sets in order:

1. `db/test/V0.0.0__CREATE_SURVEY_SCHEMA_STUBS.sql` — creates `survey.surveys`, `survey.respondents`, `survey.answers`, `survey.steps`, `survey.sections`, `survey.questions`, `survey.question_types`, `survey.operators`, `survey.actions`, and all other tables the Admin migrations FK-reference. Seeds static lookup rows.
2. `db/migration/V0.0.1__CREATE_ADMIN_SCHEMA.sql` and subsequent migrations — creates Admin tables (departments, subjects, users, etc.).

Each `@QuarkusTest` method annotated with `@TestTransaction` runs inside a rolled-back transaction, so no cleanup is needed between tests.

---

## Troubleshooting

| Problem | Likely cause | Fix |
|---------|-------------|-----|
| `relation "survey.surveys" does not exist` | `db/test` not in Flyway locations | Add `%test.quarkus.flyway.owner.locations=db/migration,db/test` |
| `Cannot connect to Docker` | Docker not running | Start Docker Desktop |
| JaCoCo check fails (`< 0.70 line coverage`) | New code not covered by tests | Add test cases or justify exclusion via `@ExcludeFromJacocoReport` |
| Karibu `_get()` throws `AssertionError` | View not registered or route path wrong | Check `@Route` annotation on the view class and verify the route string passed to `navigate()` |
