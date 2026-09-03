# Contributing to Elicit Admin

Thank you for your interest in contributing to Elicit Admin! This document provides guidelines and standards for contributing to the project.

## Table of Contents
- [Development Methodology](#development-methodology)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [Coding Standards](#coding-standards)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Domain Knowledge](#domain-knowledge)

## Development Methodology

### AI Unified Process (AIUP)

This project follows the **AI Unified Process** ([unifiedprocess.ai](https://unifiedprocess.ai/)).
The artifacts under `docs/` (`vision.md`, `requirements.md`, `entity_model.md`,
`use_cases/UC-*.md`, the use-case diagram) are the source of truth for *what* the system is
supposed to do; code is the implementation that gets regenerated/refactored around them.
If you're changing behavior, check whether the corresponding `docs/use_cases/UC-XXX-*.md`
needs updating too — and if you're adding new behavior with no matching use case, add one.

### Test Traceability — MANDATORY

Every test must be traceable to a use case: reference the `UC-XXX` ID (and, where relevant,
the specific alternative flow or business rule, e.g. `UC-004/A2`, `BR-006`) in the test class
Javadoc or method name/comment. This is not optional — it's how we keep `docs/use_cases/`
and the test suite honest about what's actually verified. Existing tests and recent commits
(e.g. `Fix Send Email reporting success on SMTP failure (UC-004/A2)`,
`Add tests for database role assignment (UC-016/UC-001)`) show the expected style.

#### Coverage

- `pom.xml` configures a JaCoCo `check` bound to the `verify` phase requiring **70% line
  coverage**. Run `./mvnw verify` (not just `./mvnw test`, which does not trigger this gate) to
  check it locally.
- **Be honest about where coverage actually stands.** As of this writing the suite does not
  yet meet that 70% bar — see `README.md`'s Testing section and
  `docs/plan/code-review-quarkus-vaadin-2.md`'s "Bonus findings". Don't assume a green
  `./mvnw test` run means the coverage gate passes; it doesn't check it.
- New code should move the needle up, not just avoid moving it down. A PR that adds a
  meaningful chunk of new logic with zero tests will be asked to add some, even though the
  overall bar isn't met yet.

#### Test types in this project

- **Unit tests** — plain JUnit 5, no Quarkus boot. Pure logic: validators, DTOs, token
  generation, entity identity (`equals`/`hashCode`).
- **Booted tests** (`@QuarkusTest`) — the full app against a throwaway Testcontainers
  PostgreSQL instance and mock OIDC (`@TestSecurity`/`@OidcSecurity` from
  `quarkus-test-security-oidc` — no real Keycloak needed). Docker must be running locally.
- **Browserless UI tests** — Vaadin views exercised server-side with no browser, via
  `com.vaadin:browserless-test-quarkus` (`QuarkusBrowserlessTest`). Views are instantiated and
  attached to the test `UI` directly (`UI.getCurrent().add(view)`) rather than reached via
  `navigate(...)`, since the Vaadin route registry isn't populated under `@QuarkusTest`. There
  is no real-browser end-to-end testing (no Vaadin TestBench) in this project.

**❌ Pull requests without any test coverage for new behavior will be rejected.**

## Technology Stack

### Backend Technologies
- **Java**: 21 (LTS)
- **Framework**: Quarkus 3.39.x
- **ORM**: Hibernate ORM with Panache — **active-record pattern only** (entities extend
  `PanacheEntity`/`PanacheEntityBase` directly; there are no `PanacheRepository` classes in
  this codebase and new code should not introduce one without discussion — active record is
  the established convention here)
- **Database**: PostgreSQL 17
- **Dependency Injection**: CDI (Jakarta EE)
- **REST APIs**: JAX-RS via `quarkus-rest` (RESTEasy Reactive)
- **Authentication**: OIDC (Keycloak for local dev/test; any OIDC-compliant provider in
  production). Three roles: `elicit_user`, `elicit_admin`, `elicit_importer` — see
  `com.elicitsoftware.security.ElicitRoles`.

### Frontend Technologies
- **Framework**: Vaadin Flow 25.2.x
- **Language**: Java (server-side rendering, no client-side JS/TS views in this project)
- **Theme**: Aura

### Testing Technologies
- **Unit Testing**: JUnit 5 (Jupiter)
- **Assertions**: JUnit 5 `Assertions` / Hamcrest matchers (via REST Assured) — this project
  does not use Mockito or AssertJ
- **Database Testing**: Testcontainers (PostgreSQL) via `PostgresTestResource`
- **Security mocking**: `quarkus-test-security` / `quarkus-test-security-oidc`
  (`@TestSecurity`, `@OidcSecurity`)
- **UI Testing**: Vaadin Browserless Testing (`browserless-test-quarkus`) — not TestBench
- **API Testing**: REST Assured
- **Coverage**: JaCoCo (`jacoco-maven-plugin`, bound to `verify`)

### Build & Tools
- **Build**: Maven (`./mvnw`)
- **CI/CD**: GitHub Actions
- **Containers**: Docker (for Testcontainers locally, and for the reference deployment)
- **Version Control**: Git
- **License headers**: enforced by `license-maven-plugin` (PolyForm Noncommercial 1.0.0) — run
  `./mvnw compile` before committing new files so headers get inserted automatically

## Getting Started

### Prerequisites
- Java 21 JDK
- Maven (or use `./mvnw`, the included wrapper)
- Docker (for Testcontainers-backed tests, and for the full reference deployment)
- IDE with Vaadin support

### Setup Development Environment

1. **Clone the repository**
```bash
git clone https://github.com/ElicitSoftware/admin.git
cd admin
```

2. **Run the full reference stack** (Postgres, Keycloak, Mailpit, and every Elicit app,
   including this one) via the `docker-compose.yml` one directory above this repo — see that
   repo's own setup instructions. This is the easiest way to get a fully working environment
   (Keycloak realm, seeded roles, seeded dev data) without configuring anything by hand.

3. **Or run Admin standalone in dev mode** against a local Postgres + Keycloak (matching the
   `%dev.*` overrides already in `src/main/resources/application.properties`: app on port
   `8081`, Postgres on `localhost:5452`, Keycloak on `localhost:8180`):
```bash
./mvnw quarkus:dev
```

4. **Run tests to verify setup** (Docker must be running for the booted/browserless tiers):
```bash
./mvnw test -Dquarkus.container-image.build=false
```

### Recommended IDE Setup

**IntelliJ IDEA:**
- Install the Quarkus plugin and the Vaadin plugin
- Enable annotation processing (required for Panache)

**VS Code:**
- Install the Extension Pack for Java and the Quarkus extension

## Coding Standards

### Naming Conventions
- **Classes**: PascalCase (e.g. `DepartmentService`, `MessageTemplate`)
- **Methods**: camelCase (e.g. `createSurvey()`, `findByXidAndDepartmentId()`)
- **Constants**: UPPER_SNAKE_CASE
- **Packages**: lowercase, under `com.elicitsoftware.*`
- **JAX-RS resource classes**: prefer a name ending in `Resource` for anything that's actually
  a `@Path`-annotated REST endpoint, to keep it visually distinct from plain CDI services in
  the same package. (`com.elicitsoftware.service` currently mixes both — see
  `docs/plan/code-review-quarkus-vaadin-2.md` finding #23 — don't extend that inconsistency in
  new code.)

### Java Features
- Java 21 features are fine: records, switch expressions, pattern matching, text blocks
- Use `@Transactional` on service/boundary methods (REST resource methods that mutate data,
  or service methods called from views) — **not** on entity or repository methods
- Prefer `io.quarkus.logging.Log` over any other logging API for consistency with the rest of
  the codebase (see commit `Standardize logging on io.quarkus.logging.Log`)

### Entity/Model Classes (Panache active record)

```java
@Entity
@Table(name = "message_templates", schema = "survey")
public class MessageTemplate extends PanacheEntityBase {

    @Id
    @SequenceGenerator(name = "message_templates_id_generator", schema = "survey",
            sequenceName = "message_templates_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_templates_id_generator")
    public long id;

    @ManyToOne
    @JoinColumn(name = "department_id")
    public Department department;

    public static MessageTemplate findByDepartment(long departmentId) {
        return find("department.id", departmentId).firstResult();
    }

    // Public fields, Panache-style static finders on the entity itself — no repository class.

    @Override
    public boolean equals(Object o) { /* ID-based — required if this entity is ever put in a
        Vaadin Grid, ComboBox, or a JPA-managed Set. See finding #11/#17 in
        docs/plan/code-review-quarkus-vaadin-2.md for what happens if you skip this. */ }

    @Override
    public int hashCode() { /* consistent with equals() */ }
}
```

If an entity's relationships default to eager fetch (JPA's implicit `@ManyToOne`/`@OneToOne`
behavior) and gets listed in bulk anywhere (a `Grid`, a report), watch for N+1 queries — either
add a `JOIN FETCH` finder (see `MessageTemplate.listAllWithDepartment()`) or rely on
`quarkus.hibernate-orm.fetch.batch-size` as a safety net, not as the primary fix for a known
hot path.

### Service Layer

```java
@ApplicationScoped
public class DepartmentService {

    @Transactional
    public Department save(Department department) {
        if (department.id == 0) {
            department.persist();
        } else {
            department = getEntityManager().merge(department);
        }
        return department;
    }
}
```

Keep persistence and business logic in services, not in Vaadin views — the 2026-07-24 code
review's finding #7 (`docs/plan/code-review-quarkus-vaadin.md`) is exactly the pattern to avoid.

### Vaadin UI Code

```java
@Route(value = "departments", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class DepartmentsView extends VerticalLayout {

    @Inject
    DepartmentService departmentService;

    private final Grid<Department> grid = new Grid<>(Department.class);

    @PostConstruct
    public void init() {
        configureGrid();
        updateList();
    }

    private void updateList() {
        grid.setItems(Department.listAll());
    }
}
```

- Always declare `@RolesAllowed`/`@PermitAll`/`@AnonymousAllowed` explicitly on every
  `@Route`-annotated view — don't rely on it being inherited from a superclass unless that's a
  deliberate, documented design choice (see `AccessDeniedErrorView` for an example of doing
  this deliberately, with a comment explaining why).
- Never bypass Vaadin's component APIs (no raw `innerHTML` — use `Span`/`Html`/text nodes).
- If a view is likely to grow past ~1000 rows of data, use lazy loading (`DataProvider`)
  instead of `listAll()` — see the `data-providers` skill and `StatusDataSource` for the
  established pattern.

## Testing Requirements

### Test Structure

```java
@Test
void statusesWithSameIdDeduplicateInSet() {
    // Arrange
    Status a = new Status();
    a.setId(100);
    Status b = new Status();
    b.setId(100);

    // Act / Assert
    assertEquals(a, b);
}
```

### Booted (`@QuarkusTest`) Tests

```java
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RespondentExportSecurityTest {

    @Test
    @TestSecurity(user = "admin", roles = {"elicit_admin"})
    void adminRoleIsAuthorized() {
        given()
            .queryParam("id", 999999)
            .when().get("/api/secured/respondent/export")
            .then()
            .statusCode(404); // reaching the resource (vs. 401/403) proves authorization passed
    }
}
```

### Browserless UI Tests

```java
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class UnauthorizedViewTest extends QuarkusBrowserlessTest {

    private UnauthorizedView attachView() {
        UnauthorizedView view = new UnauthorizedView();
        UI.getCurrent().add(view);
        return view;
    }

    @Test
    void showsAccessRestrictedHeading() {
        UnauthorizedView view = attachView();
        H1 heading = find(H1.class, view).single();
        assertEquals("Access Restricted", heading.getText());
    }
}
```

### Test Naming Convention

Descriptive method names that state the scenario and expectation, e.g.:
- `adminRoleIsAuthorized()`
- `nonAdminRoleIsForbidden()`
- `statusesWithSameIdDeduplicateInSet()`
- `keyCannotBeReplayedAfterASuccessfulDownload()`

### Running Tests

```bash
# Run all tests (Docker required for booted/browserless tiers)
./mvnw test -Dquarkus.container-image.build=false

# Run tests and generate a JaCoCo report (target/site/jacoco/index.html)
./mvnw test jacoco:report -Dquarkus.container-image.build=false

# Actually enforce the 70% coverage gate (bound to verify, not test)
./mvnw verify -Dquarkus.container-image.build=false -Dquarkus.container-image.push=false

# Run a specific test class
./mvnw test -Dtest=DepartmentServiceTest -Dquarkus.container-image.build=false

# Run the opt-in integration smoke test
./mvnw test -Dit.integration=true -Dquarkus.container-image.build=false
```

> `-Dquarkus.container-image.build=false -Dquarkus.container-image.push=false` bypasses this
> project's default behavior of building (and, on `verify`, pushing) a Docker image on every
> build — useful locally unless you actually have push access to the target registry.

## Pull Request Process

### Before Submitting

1. **Write tests, traceable to a `UC-XXX`** — see "Test Traceability" above.
2. **Run the tests** — `./mvnw test -Dquarkus.container-image.build=false`.
3. **Update `docs/`** if you changed behavior a use case describes, or added behavior with no
   use case yet.
4. **Update `README.md`** if you added an extension, endpoint, or config that a new
   contributor would need to know about.

### Commit Message Format

This project uses plain, imperative-mood commit subjects (not Conventional Commits), and
references the `UC-XXX` in parentheses when the change corresponds to a use case:

```
Add tests for database role assignment (UC-016/UC-001)
Fix Send Email reporting success on SMTP failure (UC-004/A2)
Standardize logging on io.quarkus.logging.Log
```

Branch names follow a `<type>/<short-description>` convention (matching GitHub Actions'
existing triggers): `feature/...`, `fix/...`, `chore/...`.

### Pull Request Template

```markdown
## Description
Brief description of changes

## Use Case
UC-XXX (or "adds new use case" / "no use case — infra/tooling only")

## Testing
- [ ] Tests added/updated, traceable to the use case above
- [ ] `./mvnw test -Dquarkus.container-image.build=false` passes locally

## Checklist
- [ ] `docs/` updated if behavior changed
- [ ] `README.md` updated if setup/config/endpoints changed
- [ ] No new compiler warnings introduced
```

### Code Review Guidelines

Reviewers will check:
- ✅ Tests exist for new/changed behavior and are traceable to a `UC-XXX`
- ✅ Persistence/business logic lives in services, not views
- ✅ New `@Route` views declare an explicit security annotation
- ✅ New entities used in a `Grid`/`ComboBox`/`Set` have ID-based `equals`/`hashCode`
- ✅ No secrets or real-looking credentials committed to `application.properties`
- ✅ `docs/` and `README.md` updated where relevant

## Domain Knowledge

### What Admin Does

Elicit Admin is the administration console for the Elicit survey platform (see
`docs/vision.md`). It doesn't author surveys — that's the separate **Author** tool — Admin is
for running and monitoring them:

- **Subject/respondent management** — upload subjects (CSV import or the integration API),
  generate unique survey-access **tokens**, monitor completion status.
- **Department & message template management** — departments own default message templates;
  templates support a `<TOKEN>` placeholder substituted with the respondent's real token when
  an email is sent.
- **User & role management** — `elicit_user`/`elicit_admin`/`elicit_importer`, sourced from
  OIDC by default, with an optional database fallback (`elicit.authorization.mode=DATABASE`)
  managed via `survey.user_roles`.
- **Reporting** — generates PDF reports (family-history/cancer-risk style reports) from a
  configurable, admin-defined external report-service URL per survey (`ReportDefinition`).
- **Import/export** — a custom pipe-delimited `.elicit` format for moving respondent data and
  whole survey definitions between environments.

### Key Entities (see `docs/entity_model.md` for the full ER diagram)

- **`Status`** — maps `survey.status`, a **read-only SQL view** (not a table) joining
  respondents/subjects/departments for reporting. Never `.persist()`/`.update()` it.
- **`Subject`** / **`Respondent`** — a subject is a specific person invited to a specific
  survey; a respondent record carries the unique access token.
- **`Department`**, **`MessageTemplate`**, **`MessageType`** — organizational unit and the
  emails it sends.
- **`Survey`**, **`ReportDefinition`**, **`PostSurveyAction`** — survey-level config; these
  entities map tables **owned by the separate Survey application** sharing the same `survey`
  schema (this repo's Flyway migrations only grant access to them, never `CREATE TABLE`s them).
- **`User`**, **`UserRole`** — Admin console users and their database-backed role grants.

### Getting Help

- **Documentation**: `docs/` (AIUP artifacts) and `README.md`
- **Recent code-review findings**: `docs/plan/code-review-quarkus-vaadin.md` and
  `docs/plan/code-review-quarkus-vaadin-2.md` — useful context on known issues, what's already
  been fixed, and what's intentionally deferred
- **Issues**: [GitHub Issues](https://github.com/ElicitSoftware/admin/issues)

## License

By contributing, you agree that your contributions will be licensed under the
[PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0),
matching the rest of this project (see `LICENSE.txt` and each file's license header).

---

**Thank you for contributing to Elicit Admin!**
