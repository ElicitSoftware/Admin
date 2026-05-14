# Tasks: Test Coverage for Export/Import Services

**Input**: Design documents from `/specs/000-testing/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Exact file paths are included in every task description

---

## Phase 1: Setup

**Purpose**: Add test dependencies and create the test directory skeleton.

- [X] T001 Add `com.github.mvysny.kaributesting:karibu-testing-v24:2.2.0` (test scope, pin version explicitly — not managed by Quarkus BOM) to `pom.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infrastructure that all test classes require before they can compile or run. No user-story work can begin until this phase is complete.

**⚠️ CRITICAL**: Phases 3–7 all depend on these tasks.

- [X] T002 Create `src/test/resources/db/test/V0.0.0__CREATE_SURVEY_SCHEMA_STUBS.sql` — DDL for all survey schema tables referenced by Admin Flyway FKs (`survey.surveys`, `survey.respondents`, `survey.answers`, `survey.steps`, `survey.sections`, `survey.questions`, `survey.question_types`, `survey.operators`, `survey.actions`, `survey.select_groups`, `survey.select_items`, `survey.steps_sections`, `survey.sections_questions`, `survey.relationships`, `survey.dimensions`, `survey.ontology`, `survey.metadata`, `survey.reports`, `survey.post_survey_actions`) plus static seed rows for `question_types`, `operators`, and `actions` lookup tables
- [X] T003 Add `%test` profile entries to `src/main/resources/application.properties`: `%test.quarkus.oidc.enabled=false`, `%test.quarkus.http.auth.permission.authenticated.policy=permit`, `%test.quarkus.flyway.owner.locations=db/migration,db/test`, `%test.quarkus.flyway.owner.schemas=survey`
- [X] T004 [P] Make `escapeField()` `static` package-private in `src/main/java/com/elicitsoftware/service/SurveyDefinitionExportService.java`: remove the `private` modifier and add the `static` keyword (the method is a pure string function with no instance state, so making it `static` allows `EscapeUnescapeTest` to call `SurveyDefinitionExportService.escapeField(value)` directly without CDI or `@QuarkusTest`)
- [X] T005 [P] Make `parseFields()` `static` package-private in `src/main/java/com/elicitsoftware/service/SurveyDefinitionImportService.java`: remove the `private` modifier and add the `static` keyword (pure string parsing, no instance state — same rationale as T004)

**Checkpoint**: Foundation ready — run `mvn compile` and confirm zero compilation errors before proceeding.

---

## Phase 3: User Story 1 — Survey Definition Round-Trip (Priority: P1) 🎯 MVP

**Goal**: Prove that exporting a known survey then importing it reproduces the identical structure with all FK references intact.

**Independent Test**: Run `mvn test -Dtest=SurveyDefinitionRoundTripTest` — all four test methods pass against a fresh Dev Services PostgreSQL container.

- [X] T006 [US1] Create `src/test/java/com/elicitsoftware/service/SurveyDefinitionRoundTripTest.java` as a `@QuarkusTest` class with `@Inject SurveyDefinitionExportService exportService`, `@Inject SurveyDefinitionImportService importService`, `@Inject EntityManager em`; add private fixture helper method that inserts one survey, one step, one section, two questions, one `steps_sections` join, two `sections_questions` joins, and one relationship into the test DB and returns the survey id
- [X] T007 [US1] Implement `@Test @TestTransaction surveyRoundTripPreservesElementCounts()` in `SurveyDefinitionRoundTripTest.java`: seed fixture, call `exportService.exportSurvey(surveyId)`, call `importService.importSurvey(exportString)`, then assert via `COUNT(*)` native queries that the target schema contains exactly 1 survey, 1 step, 1 section, 2 questions, 1 `steps_sections`, 2 `sections_questions`, and 1 relationship row
- [X] T008 [US1] Implement `@Test @TestTransaction headerVersionLineIsElicitSurveyExportV1()` in `SurveyDefinitionRoundTripTest.java`: seed fixture, export, split on `\n`, assert `lines[0].equals("# ELICIT_SURVEY_EXPORT_V1")` and that comment count lines match the body row counts
- [X] T009 [US1] Implement `@Test @TestTransaction escapedPipeAndNewlineRestoredAfterImport()` in `SurveyDefinitionRoundTripTest.java`: insert a survey with `name = "a|b\nc"`, export, import, query the imported survey name and assert it equals `"a|b\nc"` exactly
- [X] T010 [US1] Implement `@Test @TestTransaction allFkReferencesResolveAfterImport()` in `SurveyDefinitionRoundTripTest.java`: after round-trip, run a native query joining `steps_sections.step_id → steps.id` and `steps_sections.section_id → sections.id` and assert the join returns exactly 1 row (no orphaned FK references)

**Checkpoint**: `mvn test -Dtest=SurveyDefinitionRoundTripTest` passes. User Story 1 is independently verified.

---

## Phase 4: User Story 2 — Respondent Round-Trip (Priority: P1)

**Goal**: Prove that exporting a respondent then importing it preserves all answer fields, counts, and null values.

**Independent Test**: Run `mvn test -Dtest=RespondentRoundTripTest` — all four test methods pass.

- [X] T011 [US2] Create `src/test/java/com/elicitsoftware/service/RespondentRoundTripTest.java` as a `@QuarkusTest` class with injected `RespondentExportService`, `RespondentImportService`, and `EntityManager`; add private fixture helper that inserts a survey stub, a respondent, and three answer rows (one with `text_value = null`, one with `deleted = true`, one with a pipe character in `text_value`) and returns the respondent id
- [X] T012 [US2] Implement `@Test @TestTransaction respondentRoundTripPreservesAnswerCount()` in `RespondentRoundTripTest.java`: seed fixture, call `exportService.exportRespondent(respondentId)`, call `importService.importRespondent(exportString)`, assert `COUNT(*)` of imported answers equals 3
- [X] T013 [US2] Implement `@Test @TestTransaction respondentRoundTripPreservesTextValueAndDeletedFlag()` in `RespondentRoundTripTest.java`: after round-trip, query each imported answer by `display_key` and assert `text_value` and `deleted` match the fixture values exactly
- [X] T014 [US2] Implement `@Test @TestTransaction headerVersionLineIsElicitExportV1()` in `RespondentRoundTripTest.java`: seed fixture, export, split on `\n`, assert `lines[0].equals("# ELICIT_EXPORT_V1")`
- [X] T015 [US2] Implement `@Test @TestTransaction nullAnswerValueRoundTripsAsNull()` in `RespondentRoundTripTest.java`: after round-trip, query the answer whose `text_value` was `null` in the fixture and assert the imported `text_value` is `null` (not an empty string)

**Checkpoint**: `mvn test -Dtest=RespondentRoundTripTest` passes. User Story 2 is independently verified.

---

## Phase 5: User Story 3 — Export Format Contract (Priority: P2)

**Goal**: Assert the exact field count and column order of every export section without a database import, so spec 001 field changes are caught immediately.

**Independent Test**: Run `mvn test -Dtest=ExportFormatContractTest` — passes with no DB required (no Dev Services container started).

- [X] T016 [US3] Create `src/test/java/com/elicitsoftware/service/ExportFormatContractTest.java` as a plain JUnit 5 class (no `@QuarkusTest`, no Mockito); define a `private static final String SURVEY_EXPORT` constant containing a minimal hand-crafted `ELICIT_SURVEY_EXPORT_V1` export string with one row per section; implement `surveysLineHasSevenFields()`: find the `surveys:` data line, split on unescaped `|`, assert field count is 7 and field order matches `source_id|name|display_order|title|description|initial_display_key|post_survey_url`
- [X] T017 [US3] Implement `stepsLineHasFiveFields()` and `sectionsLineHasFiveFields()` in `ExportFormatContractTest.java`: assert each `steps:` and `sections:` data line has exactly 5 fields in order `source_id|display_order|name|dimension_name|description`
- [X] T018 [US3] Implement a single `@ParameterizedTest(name = "{0} has {1} fields") @MethodSource("sectionFieldCounts")` in `ExportFormatContractTest.java` that asserts field counts for all remaining sections; the `sectionFieldCounts()` source returns: `select_groups→4`, `select_items→5`, `steps_sections→6`, `questions→14`, `sections_questions→4`, `relationships→13`, `reports→5`, `post_survey_actions→5`, `dimensions→2`, `ontology→4`, `metadata→6`; each case finds the matching section label in `SURVEY_EXPORT`, splits on unescaped `|`, and asserts the count
- [X] T019 [US3] Implement `answersLineHasV1FieldCount()` in `ExportFormatContractTest.java`: parse a known respondent export string, find an `answers:` data line, split on unescaped `|`, assert field count equals 16 (V1 — no `question_version` field); this test MUST fail when spec 001 adds the field, signalling the parser must be updated

**Checkpoint**: `mvn test -Dtest=ExportFormatContractTest` passes with no Docker required. User Story 3 is independently verified.

---

## Phase 6: User Story 4 — Field Escape/Unescape Symmetry (Priority: P2)

**Goal**: Prove that `escapeField` and `parseFields` are perfect inverses for all special characters, with no database.

**Independent Test**: Run `mvn test -Dtest=EscapeUnescapeTest` — passes with no Dev Services container.

- [X] T020 [US4] Create `src/test/java/com/elicitsoftware/service/EscapeUnescapeTest.java` as a plain JUnit 5 class (no `@QuarkusTest`, no Quarkus startup); implement `pipeEscapedAndRestored()`: call `SurveyDefinitionExportService.escapeField("a|b")` directly as a static method, assert the result equals `"a\\|b"`, then call `SurveyDefinitionImportService.parseFields("a\\|b")` and assert `fields[0]` equals `"a|b"`
- [X] T021 [US4] Implement `backslashEscapedAndRestored()` in `EscapeUnescapeTest.java`: call `SurveyDefinitionExportService.escapeField("a\\b")` and assert the result is `"a\\\\b"`; then call `SurveyDefinitionImportService.parseFields("a\\\\b")` and assert `fields[0]` equals `"a\\b"`
- [X] T022 [US4] Implement `nullEscapesToEmptyString()` in `EscapeUnescapeTest.java`: call `SurveyDefinitionExportService.escapeField(null)` and assert the result is `""` (empty string, not `null`)
- [X] T023 [US4] Implement `newlineAndCarriageReturnPreservedAfterRoundTrip()` in `EscapeUnescapeTest.java`: call `SurveyDefinitionExportService.escapeField("line1\nline2\r\nline3")`, then pass the escaped string to `SurveyDefinitionImportService.parseFields()`, and assert `fields[0]` equals `"line1\nline2\r\nline3"` exactly

**Checkpoint**: `mvn test -Dtest=EscapeUnescapeTest` passes with no Docker required. User Story 4 is independently verified.

---

## Phase 7: User Story 5 — UI View Tests via Karibu-Testing (Priority: P2)

**Goal**: Confirm the Admin Vaadin views initialise and navigate correctly in a simulated environment, sharing the same Dev Services PostgreSQL container as the round-trip tests. No extra Docker container or packaged-app server is required.

**Independent Test**: Run `mvn test -Dtest=AdminViewTest` — all four test methods pass with Docker running (Dev Services PostgreSQL only; no browser container needed).

- [X] T024 [US5] Create `src/test/java/com/elicitsoftware/service/AdminViewTest.java` as a `@QuarkusTest` class; add `@BeforeEach void setup()` that calls `MockVaadin.setup()` (no-arg form — the Quarkus Vaadin extension registers routes when the CDI context starts, so no explicit route-scanning argument is needed); add `@AfterEach void tearDown()` that calls `MockVaadin.teardown()`
- [X] T025 [US5] Implement `@Test navigateToRootRendersAppLayout()` in `AdminViewTest.java`: call `UI.getCurrent().navigate("")`, then use `_get(AppLayout.class)` and assert it is not null
- [X] T026 [US5] Implement `@Test navigateToDepartmentsRendersGrid()` in `AdminViewTest.java`: call `UI.getCurrent().navigate("departments")`, then use `_get(Grid.class)` and assert it is not null
- [X] T027 [US5] Implement `@Test navigateToDepartmentsDoesNotThrow()` in `AdminViewTest.java`: assert that `UI.getCurrent().navigate("departments")` completes without throwing a `NotFoundException` or `NullPointerException`
- [X] T028 [US5] Implement `@Test departmentsSideNavItemExists()` in `AdminViewTest.java`: call `UI.getCurrent().navigate("")`, then use `_get(SideNavItem.class, spec -> spec.withText("Departments"))` and assert the returned component is not null

**Checkpoint**: `mvn test -Dtest=AdminViewTest` passes. User Story 5 is independently verified.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T029 Run `mvn verify` from the repo root and confirm: all 28+ test methods pass, JaCoCo reports ≥ 0.70 line coverage ratio, and the build exits with `BUILD SUCCESS`; if coverage is short, investigate `SurveyDefinitionExportService` (export loop paths), `SurveyDefinitionImportService` (parse edge cases), and `RespondentExportService`/`RespondentImportService` (answer-type branches) as the most likely under-covered classes; add targeted test cases rather than excluding classes
- [X] T030 [P] Add a one-line comment above each `%test` profile entry in `src/main/resources/application.properties` explaining its purpose (e.g. `# Test profile: disable OIDC so Vaadin views are reachable without credentials`)

---

## Phase 9: admin.flow Coverage Gate (US5 extension)

**Goal**: Confirm the `com.elicitsoftware.admin.flow` package is gated at ≥ 20% LINE coverage and expand Karibu navigation tests to reach that threshold. A separate `jacoco-check-admin-flow` execution in pom.xml enforces the gate.

**Coverage constraint**: `karibu-testing-v24` (non-CDI variant) instantiates views via `new ClassName()`, so `@PostConstruct` and `@Inject` fields are skipped. Maximum achievable coverage with navigation tests is ~20–25%. The `@PostConstruct` views (SearchView, RegisterView, EditMessageTemplatesView, MainLayout, AppConfig) account for ~860 lines that cannot be tested without CDI-aware Karibu. Switching to `quarkus-junit5-mockito` + `@InjectMock` would unlock those paths.

- [X] T031 [US5] Add `MessageTemplatesView.class`, `EditDepartmentView.class`, `EditUserView.class`, and `EditMessageTemplatesView.class` to the `Routes` set in `AdminViewTest.java` so Karibu can navigate to those routes.
- [X] T032 [US5] Add navigation tests for all newly registered views in `AdminViewTest.java`: `navigateToUsersRendersGrid`, `navigateToUsersDoesNotThrow`, `navigateToUnauthorizedDoesNotThrow`, `navigateToUnauthorizedRendersHeading`, `navigateToMessageTemplatesDoesNotThrow`, `navigateToMessageTemplatesRendersGrid`, `navigateToEditDepartmentCreateModeDoesNotThrow`, `navigateToEditUserCreateModeDoesNotThrow`, `navigateToEditMessageTemplateCreateModeDoesNotThrow`.
- [X] T033 [US5] Add `PaginationControls` unit tests in `AdminViewTest.java` exercising public methods directly within the Karibu mock context: `paginationControlsDefaultPageSizeIsTen`, `paginationControlsCalculateOffsetOnFirstPage`, `paginationControlsOnPageChangedListenerIsCalled`, `paginationControlsEmptyDatasetKeepsOnePageAndFiresListener`, `paginationControlsResetToFirstPageLeavesOffsetZero`, `paginationControlsPageSizeConsistentWithOffset`, `paginationControlsRecalculateWithoutListenerDoesNotThrow`.
- [X] T034 Add `jacoco-check-admin-flow` execution to the JaCoCo plugin in `pom.xml` scoping `com/elicitsoftware/admin/flow/*` with a `LINE` `COVEREDRATIO` minimum of `0.20`, matching the ~20% achievable with navigation tests.
- [ ] T035 (Future) To reach 70% gate on `admin.flow`: add `io.quarkus:quarkus-junit5-mockito` to pom.xml test scope; use `@InjectMock UiSessionLogin`, `@InjectMock SecurityIdentity`, and `@InjectMock StatusDataSource` in `AdminViewTest`; call `Arc.container().instance(SearchView.class).get()` after `MockVaadin.setup()` to trigger CDI instantiation and `@PostConstruct`; repeat for `RegisterView`, `EditMessageTemplatesView`, `MainLayout`, and `AppConfig`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (T001 must be in pom.xml before compiling test classes) — **blocks all user stories**
- **User Stories (Phases 3–7)**: All depend on Phase 2 completion; US1 and US2 (P1) should be completed before US3–US5 (P2)
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — no dependency on US2–US5
- **US2 (P1)**: Can start after Phase 2 — no dependency on US1, US3–US5
- **US3 (P2)**: Can start after Phase 2 — requires access to export strings; can use output from US1/US2 fixtures or build minimal strings inline
- **US4 (P2)**: Can start after Phase 2 (requires T004/T005 to make `escapeField`/`parseFields` static) — no DB dependency, no Quarkus startup
- **US5 (P2)**: Can start after Phase 2 — no dependency on US1–US4 test output

### Within Each User Story

- Skeleton class (T006, T011, T016, T020, T024) MUST be created before individual test methods in the same class
- All other test methods within a story can be added sequentially to the same file

### Parallel Opportunities

- T004 and T005 (Phase 2) can be done simultaneously — different files
- US1 and US2 can be implemented in parallel — different test classes
- US3 and US4 can be implemented in parallel with each other and with US1/US2 — pure unit tests, no DB
- US5 can proceed in parallel with US3/US4 — different test class

---

## Parallel Example: Phases 3 and 4 (both P1)

```
# Developer A: User Story 1
Task T006: Create SurveyDefinitionRoundTripTest skeleton
Task T007: surveyRoundTripPreservesElementCounts
Task T008: headerVersionLineIsElicitSurveyExportV1
Task T009: escapedPipeAndNewlineRestoredAfterImport
Task T010: allFkReferencesResolveAfterImport

# Developer B (in parallel): User Story 2
Task T011: Create RespondentRoundTripTest skeleton
Task T012: respondentRoundTripPreservesAnswerCount
Task T013: respondentRoundTripPreservesTextValueAndDeletedFlag
Task T014: headerVersionLineIsElicitExportV1
Task T015: nullAnswerValueRoundTripsAsNull
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002–T005) — CRITICAL
3. Complete Phase 3: US1 Survey Round-Trip (T006–T010)
4. Complete Phase 4: US2 Respondent Round-Trip (T011–T015)
5. **STOP and VALIDATE**: `mvn test` passes; SC-001, SC-002, SC-003 met
6. Commit and share as the safety baseline before any spec 001 work begins

### Full Delivery

1. MVP (steps 1–5 above)
2. Add Phase 5: US3 Export Format Contract (T016–T019)
3. Add Phase 6: US4 Escape/Unescape (T020–T023)
4. Add Phase 7: US5 UI Smoke Test (T024–T028)
5. Run Phase 8: Polish and coverage validation (T029–T030)
6. All 5 user stories verified; SC-001 through SC-007 met

---

## Task Count Summary

| Phase | Tasks | User Story |
|-------|-------|------------|
| Setup | 1 (T001) | — |
| Foundational | 4 (T002–T005) | — |
| US1 Survey Round-Trip | 5 (T006–T010) | P1 |
| US2 Respondent Round-Trip | 5 (T011–T015) | P1 |
| US3 Export Format Contract | 4 (T016–T019) | P2 |
| US4 Escape/Unescape | 4 (T020–T023) | P2 |
| US5 UI Smoke Test | 5 (T024–T028) | P2 |
| Polish | 2 (T029–T030) | — |
| **Total** | **30** | |
