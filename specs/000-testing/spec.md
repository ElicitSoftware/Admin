# Feature Specification: Test Coverage for Export/Import Services

**Feature Branch**: `000-testing`
**Created**: 2026-05-13
**Status**: Draft
**Input**: User description: "implement testing to protect the code when I implement spec/001"

---

## Overview

The Admin application currently has **zero automated tests**. Four services — `SurveyDefinitionExportService`, `SurveyDefinitionImportService`, `RespondentExportService`, and `RespondentImportService` — are the sole subjects of spec 001 (Kimball Type 2 SCD Support), which will significantly change their SQL queries, field layouts, and id-allocation logic.

This spec establishes a test suite that captures the current (V1) behaviour as a verified baseline. The tests must pass before any spec 001 changes are made and must continue to pass after those changes are applied, proving that backward-compatible behaviour is preserved.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Survey Definition Round-Trip (Priority: P1)

A developer runs the test suite and verifies that exporting a known survey then importing it into an empty schema produces a structurally identical survey — same element counts and all foreign-key relationships intact.

**Why this priority**: The round-trip is the primary safety net for spec 001. If the export or import changes break FK resolution, this test catches it before the defect reaches a database.

**Independent Test**: Seed a PostgreSQL test database with a minimal survey fixture (one step, one section, one question, one relationship). Call `SurveyDefinitionExportService.exportSurvey()`. Feed the resulting string to `SurveyDefinitionImportService.importSurvey()`. Assert that the target schema contains exactly the same element counts and that all FK references resolve (no orphaned rows).

**Acceptance Scenarios**:

1. **Given** a seeded survey with one step, one section, two questions, and one relationship, **When** the survey is exported then imported into an empty schema, **Then** the imported survey has exactly one step, one section, two questions, and one relationship.
2. **Given** the imported survey, **When** `steps_sections` rows are inspected, **Then** every `step_id` and `section_id` matches an existing row in `steps` and `sections` respectively (no orphaned FK references).
3. **Given** the export output, **When** the header is parsed, **Then** the first line is `# ELICIT_SURVEY_EXPORT_V1` and the element-count comment lines match the actual row counts in the body.
4. **Given** a survey whose name contains a pipe character and a newline, **When** it is exported, **Then** the pipe is escaped as `\|` and the newline is escaped as `\n` in the output; and after import the name is restored to its original value.

---

### User Story 2 — Respondent Round-Trip (Priority: P1)

A developer runs the test suite and verifies that exporting a known respondent then importing it preserves all answer rows, dependent rows, and timestamps.

**Why this priority**: `RespondentExportService` and `RespondentImportService` are modified in spec 001 to add the `question_version` field. Tests written now will immediately surface any regression if that field insertion or parsing is misaligned.

**Independent Test**: Seed a respondent with three answers in a test database. Export via `RespondentExportService.exportRespondent()`. Import via `RespondentImportService.importRespondent()`. Assert answer count, `text_value` content, and `deleted` flags match the fixture.

**Acceptance Scenarios**:

1. **Given** a finalized respondent with three answers, **When** the respondent is exported then imported into an empty schema, **Then** the imported respondent has exactly three answer rows.
2. **Given** the imported answers, **When** `text_value` and `deleted` columns are checked, **Then** every value matches the original fixture exactly.
3. **Given** the export output, **When** the header is parsed, **Then** the first line is `# ELICIT_EXPORT_V1`.
4. **Given** a respondent with a `null` answer value, **When** it is exported then imported, **Then** the imported `text_value` is `NULL` (not an empty string).

---

### User Story 3 — Export Format Contract (Priority: P2)

A developer can verify the exact field layout and column order of each export section without needing a full database import, providing fast feedback during spec 001 refactoring.

**Why this priority**: Spec 001 renames columns and adds new fields. Format-contract tests pinpoint the exact line and column that changed, making it easy to update the import parser in lockstep.

**Independent Test**: Parse the raw export string and assert that each section header lists the expected column names in the expected order, and that each data row has the correct field count.

**Acceptance Scenarios**:

1. **Given** a survey export string, **When** the `surveys:` data line is split on unescaped pipes, **Then** it has exactly 7 fields: `source_id`, `name`, `display_order`, `title`, `description`, `initial_display_key`, `post_survey_url`.
2. **Given** a survey export string, **When** the `steps:` data lines are split, **Then** each row has exactly 5 fields: `source_id`, `display_order`, `name`, `dimension_name`, `description`.
3. **Given** a survey export string, **When** the `metadata:` data lines are split, **Then** each row has exactly 6 fields: `source_id`, `step_section_id`, `question_id`, `section_question_id`, `ontology_id`, `value`.
4. **Given** a respondent export string, **When** the `answers:` data lines are split, **Then** each row has a fixed field count matching the current V1 format (without `question_version`).

---

### User Story 4 — Field Escape/Unescape Symmetry (Priority: P2)

A developer can confirm that the escape and unescape logic is inverse — any string can survive an export/import round-trip without data loss.

**Why this priority**: Spec 001 does not change the escape logic, but tests here will detect any accidental regression if the import parser is refactored.

**Independent Test**: Pass strings containing pipes, backslashes, newlines, carriage returns, and null values through the escape method and back through the unescape method. Assert round-trip identity.

**Acceptance Scenarios**:

1. **Given** the string `a|b`, **When** it is escaped then unescaped, **Then** the result is `a|b`.
2. **Given** the string `a\\b`, **When** it is escaped then unescaped, **Then** the result is `a\\b`.
3. **Given** `null`, **When** it is escaped, **Then** the result is an empty string; **When** that empty string is unescaped, **Then** the result is `null` or empty (consistent with import behaviour).
4. **Given** a string containing `\n` and `\r`, **When** it is escaped then unescaped, **Then** the original whitespace is preserved.

---

### User Story 5 — UI Smoke Tests via Karibu-Testing (Priority: P2)

A developer runs the test suite and verifies that the Admin UI views render and navigate correctly using the Karibu-Testing framework, which drives the Vaadin component tree in-process without a real browser.

**Why this priority**: Export/import services are exercised indirectly through the UI. A broken view — missing required CDI beans, misconfigured routes, or navigation errors — would prevent any manual verification of spec 001 changes. Catching rendering failures automatically reduces the feedback loop.

**Approach**: Karibu-Testing (MockVaadin) initialises a simulated Vaadin environment inside the `@QuarkusTest` JVM. Tests call `UI.getCurrent().navigate()` to trigger Vaadin's router, then inspect the resulting component tree using Karibu's `_get()` / `_find()` DSL. No browser, no extra Docker container, and no HTTP port are required — the same single Dev Services PostgreSQL container used by round-trip tests is reused.

**Independent Test**: Annotate the test class with `@QuarkusTest`. In `@BeforeEach`, call `MockVaadin.setup()`. Navigate to the root view and assert that the `AppLayout` shell component is present. Navigate to `/departments` and assert a `Grid` component is present.

**Acceptance Scenarios**:

1. **Given** MockVaadin is set up, **When** the test navigates to the root route, **Then** an `AppLayout` component is present in the component tree.
2. **Given** MockVaadin is set up, **When** the test navigates to `/departments`, **Then** a `Grid` component is present in the component tree.
3. **Given** MockVaadin is set up, **When** the test navigates to `/departments`, **Then** the navigation completes without throwing an exception.
4. **Given** MockVaadin is set up, **When** the test calls `_get(SideNavItem.class, spec -> spec.withText("Departments"))`, **Then** the item is found without exception, confirming the nav item is registered.

---

### Edge Cases

- What happens when the export is called for a survey ID that does not exist?
- What happens when the import receives an empty input stream?
- What happens when an import file has a corrupted line (wrong field count)?
- What happens when `importSurvey` is called on a file that was already imported (duplicate `source_id`)? *(deferred to spec 001 — this scenario arises during Kimball Type 2 migration rehearsals where the same export is re-imported into a target schema that already contains the data)*
---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A Quarkus integration test (`@QuarkusTest`) MUST exercise `SurveyDefinitionExportService.exportSurvey()` against a real PostgreSQL test database (via Quarkus Dev Services or Testcontainers).
- **FR-002**: A Quarkus integration test MUST exercise `SurveyDefinitionImportService.importSurvey()` against the same real PostgreSQL test database and assert FK integrity after import.
- **FR-003**: The survey round-trip test MUST assert element counts for every structural table (surveys, steps, sections, questions, steps_sections, sections_questions, relationships).
- **FR-004**: A Quarkus integration test MUST exercise `RespondentExportService.exportRespondent()` and `RespondentImportService.importRespondent()` as a round-trip.
- **FR-005**: The respondent round-trip test MUST assert answer row count, `text_value`, and `deleted` flag for each answer.
- **FR-006**: Export format contract tests MUST parse the raw export string and assert field counts per section without performing a database import.
- **FR-007**: Field escape/unescape tests MUST be pure unit tests (no database) covering pipe, backslash, newline, carriage-return, and null inputs.
- **FR-008**: All tests MUST pass on the current V1 codebase before any spec 001 changes are applied.
- **FR-009**: Test fixture data MUST be inserted and rolled back within each test transaction so tests are isolated and repeatable.
- **FR-010**: A `@QuarkusTest` class using Karibu-Testing MUST call `MockVaadin.setup()` in `@BeforeEach` to initialise a simulated Vaadin environment, and `MockVaadin.tearDown()` in `@AfterEach` to clean up.
- **FR-011**: The UI smoke test MUST navigate to the application root and assert that an `AppLayout` (or equivalent top-level Vaadin shell component) is present in the component tree; and navigate to `/departments` and assert a `Grid` component is present.
- **FR-012**: All UI tests MUST run as `@QuarkusTest` (not `@QuarkusIntegrationTest`), sharing the single Dev Services PostgreSQL container started for the round-trip integration tests.
- **FR-013**: The `jacoco-maven-plugin` MUST be configured with a `check` goal (phase `verify`) enforcing a minimum **line coverage ratio of 0.70** across the production bundle, matching the constitution's 70% floor. Quarkus-generated proxies (`**/*$$*`, `**/*_ClientProxy*`, `**/*_Subclass*`, `**/*_Bean*`) MUST be excluded from measurement. The build MUST fail if coverage drops below this threshold.

### Key Entities

- **`SurveyDefinitionExportService`**: Service under test — exports survey structure to pipe-delimited text.
- **`SurveyDefinitionImportService`**: Service under test — imports pipe-delimited text into a target schema.
- **`RespondentExportService`**: Service under test — exports respondent + answers to pipe-delimited text.
- **`RespondentImportService`**: Service under test — imports respondent + answers.
- **Survey fixture**: Minimal in-memory or SQL-seeded survey used as test input (one step, one section, two questions, one relationship).
- **Respondent fixture**: Minimal respondent with three answers and one dependent.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All test classes compile and all tests pass with `mvn test` against a fresh checkout, before any spec 001 changes.
- **SC-002**: The survey round-trip test verifies element counts for all seven structural tables and produces zero FK-integrity failures.
- **SC-003**: The respondent round-trip test verifies all answer fields (`text_value`, `deleted`) match the fixture for 100% of answer rows.
- **SC-004**: Export format contract tests assert field counts for all fourteen export sections (surveys through metadata).
- **SC-005**: All tests remain passing after spec 001 is fully implemented, confirming backward-compatible behaviour is preserved.
- **SC-006**: The Karibu UI test navigates the app root and asserts an `AppLayout` component is present; navigates to `/departments` and asserts a `Grid` component is present; all four test methods pass without throwing exceptions.
- **SC-007**: `mvn verify` reports a JaCoCo line coverage ratio ≥ 0.70 across the production bundle after all tests run; the build fails automatically if this threshold is not met.

---

## Assumptions

- The Quarkus Dev Services feature (or an equivalent Testcontainers PostgreSQL container) will be used to provide a real PostgreSQL instance for integration tests; no H2 in-memory substitute is used, because the services use PostgreSQL-specific SQL syntax.
- The `survey` schema DDL used for tests is the **pre-Kimball** schema (current V1); spec 001 tests will use a Kimball-migrated schema.
- Static lookup rows (`question_types`, `operators`, `actions`) required by import FK constraints must be seeded into the test database before the round-trip tests run.
- Test fixture data is self-contained and does not rely on any pre-existing data in the database.
- The escape/unescape tests access package-private or exposed helper methods; if those methods are private, they will be tested indirectly through the round-trip tests.
- Karibu-Testing runs entirely in-process; no external browser or Docker container is required for UI tests. The same single Dev Services PostgreSQL container used by `@QuarkusTest` round-trip tests is reused, so only one PostgreSQL container runs for the entire test suite.
- UI tests run as `@QuarkusTest` alongside the round-trip integration tests, sharing the same Dev Services PostgreSQL container. No `@QuarkusIntegrationTest` or packaged-app server is needed for UI smoke tests.
- Authentication is **not** exercised in the initial smoke tests; the test profile disables OIDC (`%test.quarkus.oidc.enabled=false`) so the Vaadin views are reachable without credentials in the MockVaadin environment.

