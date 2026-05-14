# Implementation Plan: Test Coverage for Export/Import Services

**Branch**: `specify` | **Date**: 2026-05-13 | **Spec**: [specs/000-testing/spec.md](spec.md)
**Input**: Feature specification from `/specs/000-testing/spec.md`

## Summary

Establish a baseline automated test suite (currently zero tests) covering `SurveyDefinitionExportService`, `SurveyDefinitionImportService`, `RespondentExportService`, and `RespondentImportService` before spec 001 (Kimball Type 2) changes are applied. The suite uses a **single** Quarkus Dev Services PostgreSQL container (auto-provisioned via Testcontainers) shared across all `@QuarkusTest` classes, pure JUnit 5 unit tests for escape/unescape symmetry and format contracts, and Karibu-Testing (MockVaadin) for Vaadin UI smoke tests. JaCoCo is already wired in `pom.xml` with a 70% line coverage gate.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Quarkus 3.34.1, Vaadin 25.1.1, `quarkus-jacoco`, `jacoco-maven-plugin` 0.8.12, `com.github.mvysny.kaributesting:karibu-testing-v24`
**Storage**: PostgreSQL 17, schema `survey`; Admin tables owned by Flyway at `db/migration`; survey structure tables owned by a separate service — must be pre-created via `db/test` stubs for the test DB
**Testing**: JUnit 5, `@QuarkusTest` (single Dev Services PostgreSQL shared across all integration and UI tests), Maven Surefire, `@TestTransaction` for automatic rollback, Karibu-Testing (MockVaadin) for Vaadin UI smoke tests
**Target Platform**: JVM (Linux CI, macOS dev)
**Project Type**: Web application (Quarkus REST + Vaadin full-stack)
**Performance Goals**: Full suite completes in < 5 minutes on CI
**Constraints**: No H2; PostgreSQL-specific SQL must run against a real PostgreSQL instance. Dev Services auto-provision the container with no manual setup. OIDC must be disabled in the `%test` profile so the UI is reachable without credentials.
**Scale/Scope**: 5 test classes, ~28 test methods, ≥ 70% line coverage across the production bundle

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I — Specification-First | ✅ PASS | `spec.md` written and under review |
| II — Test-First (TDD) | ✅ PASS | This spec IS the test plan; no production code written ahead of tests |
| III — Browserless UI Testing | ✅ PASS | UI tests use Karibu-Testing (MockVaadin) as required by the constitution; single Dev Services PostgreSQL shared by all `@QuarkusTest` classes |
| IV — 70% Coverage Gate | ✅ PASS | FR-013 covers this; `jacoco-maven-plugin` already added to `pom.xml` |
| V — Observability | N/A | No state-changing production operations introduced by this spec |

## Project Structure

### Documentation (this feature)

```text
specs/000-testing/
├── plan.md                        # This file
├── research.md                    # Phase 0 output
├── data-model.md                  # Phase 1 output
├── quickstart.md                  # Phase 1 output
├── contracts/
│   └── export-format-v1.md        # V1 pipe-delimited format specification
└── tasks.md                       # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── com/elicitsoftware/service/
│   │       ├── SurveyDefinitionExportService.java   # escapeField → static package-private
│   │       └── SurveyDefinitionImportService.java   # parseFields → static package-private
│   └── resources/
│       └── application.properties                   # add %test profile entries
└── test/
    ├── java/
    │   └── com/elicitsoftware/service/
    │       ├── SurveyDefinitionRoundTripTest.java    # @QuarkusTest, DB integration (US1)
    │       ├── RespondentRoundTripTest.java           # @QuarkusTest, DB integration (US2)
    │       ├── ExportFormatContractTest.java          # pure unit, no DB (US3)
    │       ├── EscapeUnescapeTest.java               # pure unit, no DB (US4)
    │       └── AdminViewTest.java                    # @QuarkusTest + Karibu-Testing (US5)
    └── resources/
        └── db/
            └── test/
                └── V0.0.0__CREATE_SURVEY_SCHEMA_STUBS.sql  # prerequisite survey tables
```

**Structure Decision**: Single-project layout scoped entirely to `src/test/`. Two minimal production-side changes are required: make `escapeField` in `SurveyDefinitionExportService` and `parseFields` in `SurveyDefinitionImportService` **`static` package-private** (remove `private`, add `static`). Both methods are pure string transformations with no instance state; making them `static` allows `EscapeUnescapeTest` to call `SurveyDefinitionExportService.escapeField(value)` from a plain JUnit 5 class — no CDI instantiation, no `@QuarkusTest`, no Quarkus startup overhead.

## Complexity Tracking

No unjustified complexity. All UI tests use Karibu-Testing as required by the project constitution. A single Dev Services PostgreSQL container is shared across all `@QuarkusTest` classes.
