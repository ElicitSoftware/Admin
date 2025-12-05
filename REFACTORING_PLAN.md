# Elicit Admin Service - Code Refactoring Plan
## Alignment with .cursorrules Standards

**Document Version:** 1.0  
**Date:** December 5, 2025  
**Repository:** Admin (ElicitSoftware)  
**Branch:** new_cursor_rules

---

## Executive Summary

This document outlines a comprehensive refactoring plan to align the Elicit Admin Service codebase with the mandatory standards defined in `.cursorrules`. The codebase currently has **0% test coverage** (vs 85% required), uses `System.out` instead of SLF4J logging, and contains multiple classes and methods that exceed size limits.

**Timeline:** 12 weeks (phased approach)  
**Priority:** CRITICAL for Phases 1-2, HIGH for Phase 3, MEDIUM for Phase 4

---

## Current State Assessment

### Technology Stack
- **Java:** 21
- **Framework:** Quarkus 3.30.2
- **UI:** Vaadin Flow 24.9.6
- **ORM:** Hibernate Panache
- **Database:** PostgreSQL 17
- **Authentication:** OIDC via Keycloak

### Critical Gaps Identified

| Issue | Current State | Required State | Severity |
|-------|--------------|----------------|----------|
| **Test Coverage** | 0% | 85%+ overall, 95%+ critical | 🔴 CRITICAL |
| **Logging** | System.out (14 instances) | SLF4J with context | 🔴 CRITICAL |
| **Class Size** | Up to 944 lines | Max 300 lines | 🟠 HIGH |
| **Method Size** | Up to 260 lines | Max 20 lines | 🟠 HIGH |
| **Repository Pattern** | None (queries in services) | Dedicated repositories | 🟠 HIGH |
| **Service Interfaces** | Concrete classes only | Interface-based design | 🟡 MEDIUM |
| **REST API Versioning** | `/secured/*` | `/api/v1/*` | 🟡 MEDIUM |
| **TODO Comments** | 15 instances | None (or ticketed) | 🟡 MEDIUM |

### Files Requiring Immediate Attention

**Critical - Code Size Violations:**
- `RegisterView.java` - 944 lines (314% over limit)
- `CsvImportService.java` - 402 lines (134% over limit)
- `User.java` - 367 lines (122% over limit)
- `TokenService.java` - 355 lines (118% over limit)
- `Respondent.java` - 353 lines (118% over limit)

**Critical - Logging Violations:**
- `EmailService.java` - 7 System.out/printStackTrace instances
- `ReportingService.java` - 1 instance
- `PDFService.java` - 1 instance
- `UiSessionLogin.java` - 1 instance

**High Priority - Method Size:**
- `RegisterView.init()` - ~260 lines
- `CsvImportService.importSubjects()` - ~100+ lines
- `TokenService.putSubject()` - ~80 lines
- `RegisterView.saveSubject()` - ~40 lines

---

## Refactoring Plan - Phased Approach

### Phase 1: Foundation (Weeks 1-4) 🔴 CRITICAL

#### Week 1: Establish Test Infrastructure
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** CRITICAL (MANDATORY requirement)

**Objectives:**
- Set up JUnit 5 test framework
- Configure Mockito for mocking
- Set up Testcontainers for PostgreSQL integration tests
- Configure test profiles in `pom.xml`
- Create test directory structure
- Establish CI/CD test pipeline

**Deliverables:**
```
src/test/java/com/elicitsoftware/
├── service/
│   ├── TokenServiceTest.java
│   ├── EmailServiceTest.java
│   └── CsvImportServiceTest.java
├── model/
│   ├── SubjectTest.java
│   └── RespondentTest.java
├── integration/
│   ├── TokenServiceIntegrationTest.java
│   └── DatabaseIntegrationTest.java
└── testcontainers/
    └── PostgresTestResource.java
```

**Acceptance Criteria:**
- [ ] Tests run in Maven lifecycle (`mvn test`)
- [ ] Testcontainers PostgreSQL starts successfully
- [ ] At least 3 critical service tests passing
- [ ] Test coverage reporting configured (JaCoCo)
- [ ] Initial coverage baseline documented

**Files to Create:**
- `src/test/java/com/elicitsoftware/service/TokenServiceTest.java`
- `src/test/java/com/elicitsoftware/service/EmailServiceTest.java`
- `src/test/java/com/elicitsoftware/service/CsvImportServiceTest.java`
- `src/test/resources/application-test.properties`
- `src/test/resources/test-data.sql`

**Dependencies to Add:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

---

#### Week 2: Replace System.out with SLF4J Logging
**Status:** Not Started  
**Effort:** 2 days  
**Priority:** CRITICAL (MANDATORY requirement)

**Objectives:**
- Replace all `System.out.println()` with `log.info()` or `log.debug()`
- Replace all `printStackTrace()` with `log.error(message, exception)`
- Add logger fields to all affected classes
- Add MDC context (user ID, request ID) where appropriate
- Configure JSON logging for production

**Files to Modify:**
1. `src/main/java/com/elicitsoftware/service/EmailService.java` (7 violations)
2. `src/main/java/com/elicitsoftware/report/ReportingService.java` (1 violation)
3. `src/main/java/com/elicitsoftware/service/PDFService.java` (1 violation)
4. `src/main/java/com/elicitsoftware/security/UiSessionLogin.java` (1 violation)

**Pattern to Follow:**

**BEFORE:**
```java
public class EmailService {
    public void sendEmail() {
        System.out.println("Sending email for status: " + status.getToken());
        try {
            // send logic
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

**AFTER:**
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
    public void sendEmail() {
        log.info("Sending email for status: {}", status.getToken());
        try {
            // send logic
        } catch (Exception e) {
            log.error("Failed to send email for status: {}", status.getToken(), e);
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Zero instances of `System.out` in production code
- [ ] Zero instances of `printStackTrace()` in production code
- [ ] All classes have proper `Logger` fields
- [ ] Parameterized logging used throughout
- [ ] MDC context added for user/request tracking
- [ ] Grep search confirms no violations: `grep -r "System.out\|printStackTrace" src/main/java/`

---

#### Week 3: Extract Repository Layer
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** HIGH

**Objectives:**
- Create repository interfaces for all entities
- Extract all Panache queries from services/views into repositories
- Implement repository pattern while preserving Panache benefits
- Update services to use constructor-injected repositories
- Add repository tests

**Repository Interfaces to Create:**

```java
// src/main/java/com/elicitsoftware/repository/SubjectRepository.java
package com.elicitsoftware.repository;

import com.elicitsoftware.model.Subject;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SubjectRepository implements PanacheRepository<Subject> {
    
    public Optional<Subject> findByToken(String token) {
        return find("token", token).firstResultOptional();
    }
    
    public Optional<Subject> findByXid(String xid) {
        return find("xid", xid).firstResultOptional();
    }
    
    public List<Subject> findByDepartment(Long departmentId) {
        return list("department.id", departmentId);
    }
    
    public List<Subject> findPendingMessages() {
        return list("status = ?1 and sentDate is null", "pending");
    }
    
    public long countByDepartment(Long departmentId) {
        return count("department.id", departmentId);
    }
}
```

**Repositories to Create:**
- `SubjectRepository` (from `Subject` entity)
- `RespondentRepository` (from `Respondent` entity)
- `UserRepository` (from `User` entity)
- `DepartmentRepository` (from `Department` entity)
- `TemplateRepository` (from `Template` entity)
- `ParticipantRepository` (from `Participant` entity)
- `SurveyRepository` (from `Survey` entity)

**Services to Update:**
- `TokenService` - inject `SubjectRepository`, `RespondentRepository`
- `EmailService` - inject `SubjectRepository`
- `CsvImportService` - inject `SubjectRepository`, `DepartmentRepository`
- `ReportingService` - inject `ParticipantRepository`, `SurveyRepository`

**Pattern:**

**BEFORE (in TokenService):**
```java
Subject subject = Subject.find("xid", request.xid).firstResult();
```

**AFTER (in TokenService):**
```java
private final SubjectRepository subjectRepository;

public TokenService(SubjectRepository subjectRepository) {
    this.subjectRepository = subjectRepository;
}

Subject subject = subjectRepository.findByXid(request.xid)
    .orElseThrow(() -> new NotFoundException("Subject not found"));
```

**Acceptance Criteria:**
- [ ] All repository interfaces created
- [ ] Zero direct Panache static calls in services/views
- [ ] All repositories tested
- [ ] Constructor injection used throughout
- [ ] Optional pattern used for nullable results

---

#### Week 4: Create Service Interfaces
**Status:** Not Started  
**Effort:** 4 days  
**Priority:** HIGH

**Objectives:**
- Extract interfaces from all `@ApplicationScoped` services
- Follow TDD principle: Interface → Implementation
- Enable proper mocking in tests
- Separate API contract from implementation

**Service Interfaces to Create:**

```java
// src/main/java/com/elicitsoftware/service/TokenService.java (interface)
package com.elicitsoftware.service;

import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import java.util.List;

/**
 * Service for managing subject tokens and survey participation.
 */
public interface TokenService {
    
    /**
     * Adds or updates a single subject.
     * @param request the subject details
     * @return response with token and status
     */
    AddResponse putSubject(AddRequest request);
    
    /**
     * Adds or updates multiple subjects in batch.
     * @param requests list of subject details
     * @return list of responses with tokens
     */
    List<AddResponse> putSubjects(List<AddRequest> requests);
    
    /**
     * Retrieves a token for survey participation.
     * @param surveyId the survey identifier
     * @return respondent with token
     */
    Respondent getToken(int surveyId);
}

// src/main/java/com/elicitsoftware/service/impl/TokenServiceImpl.java
package com.elicitsoftware.service.impl;

import com.elicitsoftware.service.TokenService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenServiceImpl implements TokenService {
    // Implementation moved here
}
```

**Services to Refactor:**
1. `TokenService` → `TokenService` (interface) + `TokenServiceImpl`
2. `EmailService` → `EmailService` (interface) + `EmailServiceImpl`
3. `CsvImportService` → `CsvImportService` (interface) + `CsvImportServiceImpl`
4. `PDFService` → `PDFService` (interface) + `PDFServiceImpl`
5. `ReportingService` → `ReportingService` (interface) + `ReportingServiceImpl`

**Package Structure:**
```
com/elicitsoftware/service/
├── TokenService.java (interface)
├── EmailService.java (interface)
├── CsvImportService.java (interface)
└── impl/
    ├── TokenServiceImpl.java
    ├── EmailServiceImpl.java
    └── CsvImportServiceImpl.java
```

**Acceptance Criteria:**
- [ ] All service interfaces created
- [ ] Implementation classes in `impl` package
- [ ] No breaking changes to existing injection points
- [ ] All tests updated to mock interfaces
- [ ] Javadoc on interface methods

---

### Phase 2: Code Quality (Weeks 5-7) 🟠 HIGH

#### Week 5: Refactor RegisterView
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** HIGH (944 lines → <300 lines)

**Objectives:**
- Split `RegisterView.java` (944 lines) into focused components
- Extract form creation logic
- Extract validation logic
- Extract persistence logic
- Extract documentation/help content
- Ensure each component stays under 300 lines

**Target Structure:**

```
src/main/java/com/elicitsoftware/admin/flow/
├── RegisterView.java (<250 lines) - Main view orchestration
├── component/
│   ├── SubjectFormComponent.java (<250 lines) - Form UI and binding
│   ├── CsvDocumentationComponent.java (<200 lines) - CSV help content
│   └── SubjectFormBinder.java (<150 lines) - Binder configuration
├── validator/
│   └── SubjectFormValidator.java (<150 lines) - Form validation
└── service/
    └── SubjectPersistenceService.java (<200 lines) - Save/update operations
```

**Key Extractions:**

1. **SubjectFormComponent** - Extract form layout and field creation
   - Lines 210-470 of current `init()` method
   - Form layout, field configuration, styling
   - Event handlers for field interactions

2. **SubjectFormBinder** - Extract binder configuration
   - Binder setup and field bindings
   - Validation rules
   - Converters and formatters

3. **SubjectFormValidator** - Extract validation logic
   - Custom validation rules
   - Cross-field validation
   - Business rule validation

4. **SubjectPersistenceService** - Extract save/update logic
   - Transaction management
   - Entity persistence
   - Notification logic

5. **CsvDocumentationComponent** - Extract CSV help content
   - Documentation text
   - Example formatting
   - Help dialog creation

**RegisterView (After Refactoring):**
```java
@Route(value = "register", layout = MainLayout.class)
@RolesAllowed("elicit_admin")
public class RegisterView extends VerticalLayout implements BeforeEnterObserver {
    
    private final SubjectFormComponent formComponent;
    private final SubjectPersistenceService persistenceService;
    private final CsvDocumentationComponent docComponent;
    
    public RegisterView(
            SubjectFormComponent formComponent,
            SubjectPersistenceService persistenceService,
            CsvDocumentationComponent docComponent) {
        this.formComponent = formComponent;
        this.persistenceService = persistenceService;
        this.docComponent = docComponent;
        init();
    }
    
    private void init() {
        add(createHeader());
        add(formComponent);
        add(createButtonBar());
        configureSaveButton();
    }
    
    // Each method < 20 lines
    private Component createHeader() { /* ... */ }
    private Component createButtonBar() { /* ... */ }
    private void configureSaveButton() { /* ... */ }
    private void handleSave() { /* ... */ }
}
```

**Acceptance Criteria:**
- [ ] `RegisterView.java` < 250 lines
- [ ] All extracted components < 300 lines
- [ ] All methods < 20 lines
- [ ] No functionality lost
- [ ] All tests passing
- [ ] UI behavior unchanged

---

#### Week 6: Refactor CsvImportService and TokenService
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** HIGH

**Objectives:**
- Split `CsvImportService.java` (402 lines → <300 lines)
- Split `TokenService.java` (355 lines → <300 lines)
- Extract parsing, validation, and error handling into separate classes
- Extract REST resource from business logic

**CsvImportService Refactoring:**

**Target Structure:**
```
src/main/java/com/elicitsoftware/service/
├── CsvImportService.java (interface)
└── impl/
    ├── CsvImportServiceImpl.java (<250 lines) - Orchestration
    ├── CsvParser.java (<200 lines) - CSV parsing logic
    ├── CsvValidator.java (<150 lines) - Row validation
    └── CsvErrorHandler.java (<150 lines) - Error collection and reporting
```

**Key Extractions:**

1. **CsvParser**
   - File reading and parsing
   - Column mapping
   - Data type conversion
   - Handles CSV format variations

2. **CsvValidator**
   - Row-level validation
   - Required field checks
   - Format validation (email, phone)
   - Business rule validation

3. **CsvErrorHandler**
   - Error collection by row
   - Error message formatting
   - Summary generation
   - Error reporting

**TokenService Refactoring:**

**Target Structure:**
```
src/main/java/com/elicitsoftware/
├── service/
│   ├── TokenService.java (interface)
│   └── impl/
│       └── TokenServiceImpl.java (<250 lines) - Business logic only
└── resource/
    └── SubjectResource.java (<200 lines) - REST endpoints
```

**Key Extractions:**

1. **SubjectResource** - Extract REST layer
   - JAX-RS annotations and endpoint methods
   - Request/response mapping
   - HTTP status code handling
   - OpenAPI annotations

2. **TokenServiceImpl** - Pure business logic
   - Subject creation/update logic
   - Token generation
   - Validation coordination
   - Transaction orchestration

**Pattern:**

**BEFORE (TokenService - mixed concerns):**
```java
@ApplicationScoped
@Path("/secured")
public class TokenService {
    
    @POST
    @Path("/add/subject")
    public Response putSubject(AddRequest request) {
        // 80 lines of business logic mixed with REST concerns
    }
}
```

**AFTER (Separated concerns):**

```java
// Business logic
@ApplicationScoped
public class TokenServiceImpl implements TokenService {
    @Override
    @Transactional
    public AddResponse putSubject(AddRequest request) {
        // Pure business logic, no JAX-RS
    }
}

// REST layer
@Path("/api/v1/subjects")
@ApplicationScoped
public class SubjectResource {
    private final TokenService tokenService;
    
    @POST
    @Operation(summary = "Add or update a subject")
    public Response addSubject(@Valid AddRequest request) {
        AddResponse response = tokenService.putSubject(request);
        return Response.ok(response).build();
    }
}
```

**Acceptance Criteria:**
- [ ] `CsvImportService` split into 4 focused classes
- [ ] `TokenService` business logic < 250 lines
- [ ] REST endpoints extracted to `SubjectResource`
- [ ] All classes < 300 lines
- [ ] All methods < 20 lines
- [ ] Comprehensive tests for each new class
- [ ] No functionality lost

---

#### Week 7: Extract Long Methods Across Codebase
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** HIGH

**Objectives:**
- Identify all methods exceeding 20 lines
- Extract helper methods
- Improve readability and testability
- Apply Extract Method refactoring pattern

**Target Methods:**

1. **RegisterView**
   - `init()` - 260 lines → Multiple methods < 20 lines each
   - `saveSubject()` - 40 lines → Extract validation, persistence, notification
   - `beforeEnter()` - 35 lines → Extract parameter handling, entity loading

2. **EmailService**
   - `sendEmail()` - 50+ lines → Extract template rendering, sending, error handling
   - `processPendingEmails()` - 40+ lines → Extract filtering, sending, status update

3. **CsvImportService**
   - `importSubjects()` - 100+ lines → (Already addressed in Week 6)

4. **TokenService**
   - `putSubject()` - 80 lines → (Already addressed in Week 6)

5. **User.java**
   - Extract getter/setter methods if violating patterns
   - Consider using Lombok annotations

**Pattern - Extract Method:**

**BEFORE:**
```java
public void saveSubject() {
    // Line 1-10: Validation
    if (firstName == null || firstName.isEmpty()) {
        showError("First name required");
        return;
    }
    if (email == null || !email.contains("@")) {
        showError("Valid email required");
        return;
    }
    
    // Line 11-25: Persistence
    Subject subject = new Subject();
    subject.setFirstName(firstName);
    subject.setLastName(lastName);
    subject.setEmail(email);
    subject.setDepartment(department);
    subject.persist();
    
    // Line 26-40: Notification
    emailService.sendWelcomeEmail(subject);
    showSuccess("Subject saved successfully");
    clearForm();
}
```

**AFTER:**
```java
public void saveSubject() {
    if (!validateForm()) {
        return;
    }
    Subject subject = persistSubject();
    notifySuccess(subject);
}

private boolean validateForm() {
    if (!isValidName(firstName)) {
        showError("First name required");
        return false;
    }
    if (!isValidEmail(email)) {
        showError("Valid email required");
        return false;
    }
    return true;
}

private Subject persistSubject() {
    Subject subject = createSubjectFromForm();
    subject.persist();
    return subject;
}

private void notifySuccess(Subject subject) {
    emailService.sendWelcomeEmail(subject);
    showSuccess("Subject saved successfully");
    clearForm();
}
```

**Tools to Use:**
- IDE refactoring: Extract Method (⌘⌥M in IntelliJ)
- Static analysis: Checkstyle with MethodLength rule
- Manual review of grep results: `grep -n "public\|private\|protected" src/main/java/**/*.java`

**Acceptance Criteria:**
- [ ] Zero methods exceeding 20 lines in critical classes
- [ ] All extracted methods have clear, single responsibilities
- [ ] Method names describe their purpose clearly
- [ ] All tests updated and passing
- [ ] Code coverage maintained or improved

---

### Phase 3: Standards Compliance (Weeks 8-10) 🟡 MEDIUM

#### Week 8: Standardize REST APIs with Versioning
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** MEDIUM

**Objectives:**
- Add `/api/v1/` prefix to all REST endpoints
- Implement consistent error response structure
- Add OpenAPI annotations for API documentation
- Add request/response validation
- Implement pagination for collections
- Consider HATEOAS links where beneficial

**Current Endpoints → New Endpoints:**

| Current Endpoint | New Endpoint | Method | Description |
|-----------------|--------------|--------|-------------|
| `/secured/add/subject` | `/api/v1/subjects` | POST | Add single subject |
| `/secured/add/subjects` | `/api/v1/subjects/batch` | POST | Add multiple subjects |
| `/secured/add/csv` | `/api/v1/subjects/import` | POST | CSV bulk import |
| `/api/brand/{survey}` | `/api/v1/surveys/{surveyId}/brand` | GET | Get brand assets |

**Error Response Structure:**

```java
// src/main/java/com/elicitsoftware/response/ErrorResponse.java
package com.elicitsoftware.response;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private List<ValidationError> validationErrors;
    
    public static class ValidationError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
```

**OpenAPI Annotations:**

```java
@Path("/api/v1/subjects")
@Tag(name = "Subjects", description = "Subject management operations")
@ApplicationScoped
public class SubjectResource {
    
    @POST
    @Operation(
        summary = "Add or update a subject",
        description = "Creates a new subject or updates existing one based on XID"
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Subject created/updated successfully",
            content = @Content(schema = @Schema(implementation = AddResponse.class))
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @APIResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    public Response addSubject(
            @Valid 
            @RequestBody(
                description = "Subject details",
                required = true,
                content = @Content(schema = @Schema(implementation = AddRequest.class))
            )
            AddRequest request) {
        // Implementation
    }
}
```

**Request Validation:**

```java
// src/main/java/com/elicitsoftware/request/AddRequest.java
public class AddRequest {
    
    @NotBlank(message = "External ID is required")
    @Size(min = 1, max = 255, message = "External ID must be between 1 and 255 characters")
    private String xid;
    
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;
    
    @Email(message = "Must be a valid email address")
    @NotBlank(message = "Email is required")
    private String email;
    
    @Pattern(regexp = "^[0-9\\-\\+\\s\\(\\)]*$", message = "Invalid phone number format")
    private String phone;
    
    @Min(value = 1, message = "Survey ID must be positive")
    private int surveyId;
}
```

**Pagination Support:**

```java
@GET
@Path("/api/v1/subjects")
@Operation(summary = "List subjects with pagination")
public Response listSubjects(
        @QueryParam("page") @DefaultValue("0") int page,
        @QueryParam("size") @DefaultValue("20") int size,
        @QueryParam("departmentId") Long departmentId) {
    
    PanacheQuery<Subject> query = departmentId != null 
        ? Subject.find("department.id", departmentId)
        : Subject.findAll();
    
    List<Subject> subjects = query.page(page, size).list();
    long totalCount = query.count();
    
    PagedResponse<Subject> response = new PagedResponse<>(
        subjects,
        page,
        size,
        totalCount
    );
    
    return Response.ok(response).build();
}

// PagedResponse.java
public class PagedResponse<T> {
    private List<T> content;
    private PageMetadata page;
    
    public static class PageMetadata {
        private int number;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
```

**Backward Compatibility Strategy:**

Option 1: Maintain legacy endpoints as deprecated proxies
```java
@Path("/secured")
@Deprecated
public class LegacyTokenResource {
    
    @Inject
    SubjectResource subjectResource;
    
    @POST
    @Path("/add/subject")
    @Deprecated
    public Response putSubject(AddRequest request) {
        // Proxy to new endpoint
        return subjectResource.addSubject(request);
    }
}
```

Option 2: Coordinate breaking change with clients (preferred for internal APIs)

**Acceptance Criteria:**
- [ ] All endpoints moved to `/api/v1/*` structure
- [ ] Consistent error responses across all endpoints
- [ ] OpenAPI documentation generated at `/q/openapi`
- [ ] Swagger UI available at `/q/swagger-ui`
- [ ] Request validation annotations added
- [ ] Pagination implemented for collection endpoints
- [ ] Decision made on backward compatibility
- [ ] API documentation updated

---

#### Week 9: Remove TODOs and Extract Constants
**Status:** Not Started  
**Effort:** 3 days  
**Priority:** MEDIUM

**Objectives:**
- Resolve or remove all 15 TODO comments
- Extract magic numbers into named constants
- Extract hard-coded strings into configuration or constants
- Create constants classes following best practices

**TODO Comments to Address:**

1. **Subject.java (line 217)**
```java
//todo add created date
```
**Action:** Add `@CreationTimestamp` field or remove comment if not needed

2. **MainLayout.java (line 224)**
```java
// TODO this is a hack! Restore the if statement after the OIDC is fixed.
```
**Action:** Investigate OIDC issue, fix properly, or create ticket and reference it

3. **RespondentValidator.java (multiple)**
```java
//   <li>TODO: Consider requiring email address only (as SMS is no longer used)</li>
//   <li>TODO: May be replaced with standard JPA validation annotations</li>
//   <li>TODO: This entire validator package may be removed in future versions</li>
```
**Action:** Make architectural decision and update accordingly

**Magic Numbers to Extract:**

**BEFORE:**
```java
// TokenService.java
int tries = 4; // No explanation why 4

// RegisterView.java
phone.setPlaceholder("123-456-7890");
email.setPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

// Hard-coded survey ID
subject.setSurveyId(respondent.survey.id);
```

**AFTER:**
```java
// Constants class
package com.elicitsoftware.constant;

public final class ValidationConstants {
    private ValidationConstants() {} // Prevent instantiation
    
    public static final int MAX_TOKEN_GENERATION_ATTEMPTS = 4;
    public static final String PHONE_PLACEHOLDER = "123-456-7890";
    public static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 255;
}

public final class MessageConstants {
    private MessageConstants() {}
    
    public static final String ERROR_REQUIRED_FIELD = "%s is required";
    public static final String ERROR_INVALID_EMAIL = "Must be a valid email address";
    public static final String SUCCESS_SUBJECT_SAVED = "Subject saved successfully";
}

// Usage
int tries = ValidationConstants.MAX_TOKEN_GENERATION_ATTEMPTS;
phone.setPlaceholder(ValidationConstants.PHONE_PLACEHOLDER);
```

**Constants Classes to Create:**

```
src/main/java/com/elicitsoftware/constant/
├── ValidationConstants.java - Validation rules and patterns
├── MessageConstants.java - User-facing messages
├── ConfigConstants.java - Configuration keys
├── SecurityConstants.java - Roles, permissions
└── EmailConstants.java - Email templates, subjects
```

**Configuration Externalization:**

Move hard-coded values to `application.properties`:

```properties
# application.properties
app.token.max-generation-attempts=4
app.email.from-address=noreply@elicit.com
app.phone.placeholder=123-456-7890
app.validation.email.pattern=^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$
```

```java
// Inject config values
@ConfigProperty(name = "app.token.max-generation-attempts")
int maxAttempts;
```

**Acceptance Criteria:**
- [ ] Zero TODO comments (resolved or ticketed)
- [ ] Zero magic numbers in business logic
- [ ] All hard-coded strings extracted to constants or config
- [ ] Constants classes created and documented
- [ ] Configuration externalized where appropriate
- [ ] Grep confirms no violations: `grep -ri "TODO\|FIXME" src/main/java/`

---

#### Week 10: Add OpenAPI Documentation and Validation
**Status:** Not Started  
**Effort:** 4 days  
**Priority:** MEDIUM

**Objectives:**
- Complete OpenAPI annotations for all endpoints
- Add comprehensive API examples
- Add schema descriptions
- Configure Swagger UI
- Add request/response examples
- Document error responses

**OpenAPI Configuration:**

```java
// src/main/java/com/elicitsoftware/config/OpenApiConfig.java
package com.elicitsoftware.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;

@OpenAPIDefinition(
    info = @Info(
        title = "Elicit Admin API",
        version = "1.0.0",
        description = "Administrative API for managing survey subjects, departments, and templates",
        contact = @Contact(
            name = "Elicit Software Support",
            email = "support@elicit.com"
        ),
        license = @License(
            name = "Proprietary",
            url = "https://elicit.com/license"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development"),
        @Server(url = "https://admin.elicit.com", description = "Production")
    },
    security = @SecurityRequirement(name = "OIDC")
)
@SecurityScheme(
    securitySchemeName = "OIDC",
    type = SecuritySchemeType.OPENIDCONNECT,
    openIdConnectUrl = "https://auth.elicit.com/.well-known/openid-configuration"
)
public class OpenApiApplication extends Application {
}
```

**Complete Endpoint Documentation:**

```java
@Path("/api/v1/subjects")
@Tag(name = "Subjects", description = "Subject management operations")
public class SubjectResource {
    
    @POST
    @Operation(
        summary = "Add or update a subject",
        description = """
            Creates a new subject or updates an existing one based on the external ID (xid).
            If a subject with the given xid exists, their information will be updated.
            Otherwise, a new subject will be created with a generated token.
            """
    )
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Subject created or updated successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AddResponse.class),
                examples = @ExampleObject(
                    name = "success",
                    value = """
                        {
                          "token": "ABC123XYZ",
                          "xid": "EMP001",
                          "status": "created",
                          "message": "Subject created successfully"
                        }
                        """
                )
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Invalid request data",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "validation-error",
                    value = """
                        {
                          "timestamp": "2025-12-05T10:30:00Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Validation failed",
                          "path": "/api/v1/subjects",
                          "validationErrors": [
                            {
                              "field": "email",
                              "message": "Must be a valid email address",
                              "rejectedValue": "invalid-email"
                            }
                          ]
                        }
                        """
                )
            )
        )
    })
    public Response addSubject(
            @Valid 
            @RequestBody(
                description = "Subject information including personal details and survey assignment",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AddRequest.class),
                    examples = @ExampleObject(
                        name = "new-subject",
                        value = """
                            {
                              "xid": "EMP001",
                              "firstName": "John",
                              "lastName": "Doe",
                              "email": "john.doe@company.com",
                              "phone": "555-123-4567",
                              "surveyId": 1,
                              "departmentId": 10
                            }
                            """
                    )
                )
            )
            AddRequest request) {
        // Implementation
    }
}
```

**Schema Documentation:**

```java
@Schema(description = "Request to add or update a subject")
public class AddRequest {
    
    @Schema(
        description = "External ID from source system (unique identifier)",
        example = "EMP001",
        required = true,
        maxLength = 255
    )
    @NotBlank
    private String xid;
    
    @Schema(
        description = "Subject's first name",
        example = "John",
        required = true,
        maxLength = 100
    )
    @NotBlank
    private String firstName;
    
    @Schema(
        description = "Subject's email address (used for survey invitations)",
        example = "john.doe@company.com",
        required = true,
        format = "email"
    )
    @Email
    private String email;
    
    // ... other fields
}
```

**Swagger UI Configuration:**

```properties
# application.properties
quarkus.swagger-ui.always-include=true
quarkus.swagger-ui.path=/swagger-ui
quarkus.swagger-ui.theme=outline
quarkus.swagger-ui.title=Elicit Admin API
quarkus.swagger-ui.urls-primary-name=v1
```

**Acceptance Criteria:**
- [ ] All endpoints have complete OpenAPI annotations
- [ ] All DTOs have schema descriptions
- [ ] Request/response examples provided
- [ ] Error responses documented
- [ ] Swagger UI accessible at `/q/swagger-ui`
- [ ] OpenAPI spec accessible at `/q/openapi`
- [ ] API documentation reviewed for accuracy
- [ ] Security schemes documented

---

### Phase 4: Enhancements (Weeks 11-12) 🟢 LOW

#### Week 11: Implement Comprehensive Audit Logging
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** LOW

**Objectives:**
- Create `AuditLog` entity for tracking admin actions
- Implement `AuditService` for recording events
- Create interceptors for automatic audit logging
- Add async processing for performance
- Create audit log queries and reports

**Audit Log Entity:**

```java
// src/main/java/com/elicitsoftware/model/AuditLog.java
package com.elicitsoftware.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_action", columnList = "action")
})
public class AuditLog extends PanacheEntity {
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "user_email")
    private String userEmail;
    
    @Column(nullable = false, length = 50)
    private String action; // CREATE, UPDATE, DELETE, IMPORT, EXPORT
    
    @Column(name = "entity_type", length = 100)
    private String entityType; // Subject, Department, Template
    
    @Column(name = "entity_id")
    private Long entityId;
    
    @Column(name = "entity_identifier")
    private String entityIdentifier; // XID, email, name
    
    @Column(length = 50)
    private String ipAddress;
    
    @Column(length = 4000)
    private String details; // JSON with change details
    
    @Column(length = 50)
    private String result; // SUCCESS, FAILURE
    
    @Column(length = 1000)
    private String errorMessage;
    
    // Constructors, getters, setters
}
```

**Audit Service:**

```java
// src/main/java/com/elicitsoftware/service/AuditService.java
package com.elicitsoftware.service;

import com.elicitsoftware.model.AuditLog;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

@ApplicationScoped
public class AuditService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    
    @Inject
    SecurityIdentity identity;
    
    /**
     * Records an audit log entry asynchronously.
     */
    @Transactional
    public void logAction(String action, String entityType, Long entityId, 
                         String entityIdentifier, String details) {
        try {
            AuditLog log = new AuditLog();
            log.setTimestamp(Instant.now());
            log.setUserId(identity.getPrincipal().getName());
            log.setUserEmail(getUserEmail());
            log.setAction(action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setEntityIdentifier(entityIdentifier);
            log.setDetails(details);
            log.setResult("SUCCESS");
            log.persist();
        } catch (Exception e) {
            log.error("Failed to create audit log entry", e);
        }
    }
    
    public void logFailure(String action, String entityType, String errorMessage) {
        try {
            AuditLog log = new AuditLog();
            log.setTimestamp(Instant.now());
            log.setUserId(identity.getPrincipal().getName());
            log.setAction(action);
            log.setEntityType(entityType);
            log.setResult("FAILURE");
            log.setErrorMessage(errorMessage);
            log.persist();
        } catch (Exception e) {
            log.error("Failed to create audit log entry for failure", e);
        }
    }
    
    private String getUserEmail() {
        // Extract from identity attributes
        return identity.getAttribute("email");
    }
}
```

**Audit Interceptor:**

```java
// src/main/java/com/elicitsoftware/interceptor/AuditInterceptor.java
package com.elicitsoftware.interceptor;

import com.elicitsoftware.service.AuditService;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.lang.annotation.*;

@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Audited {
    String action();
    String entityType();
}

@Audited(action = "", entityType = "")
@Interceptor
public class AuditInterceptor {
    
    @Inject
    AuditService auditService;
    
    @AroundInvoke
    public Object audit(InvocationContext context) throws Exception {
        Audited audited = context.getMethod().getAnnotation(Audited.class);
        
        try {
            Object result = context.proceed();
            
            // Extract entity details from result
            auditService.logAction(
                audited.action(),
                audited.entityType(),
                extractEntityId(result),
                extractEntityIdentifier(result),
                createDetailsJson(context.getParameters())
            );
            
            return result;
        } catch (Exception e) {
            auditService.logFailure(
                audited.action(),
                audited.entityType(),
                e.getMessage()
            );
            throw e;
        }
    }
    
    private Long extractEntityId(Object result) { /* ... */ }
    private String extractEntityIdentifier(Object result) { /* ... */ }
    private String createDetailsJson(Object[] params) { /* ... */ }
}
```

**Usage:**

```java
@ApplicationScoped
public class TokenServiceImpl implements TokenService {
    
    @Audited(action = "CREATE", entityType = "Subject")
    @Override
    @Transactional
    public AddResponse putSubject(AddRequest request) {
        // Implementation - automatically audited
    }
}
```

**Acceptance Criteria:**
- [ ] `AuditLog` entity created with proper indexes
- [ ] `AuditService` implemented and tested
- [ ] Audit interceptor created
- [ ] Critical operations annotated with `@Audited`
- [ ] Async processing configured for performance
- [ ] Audit log query endpoints created
- [ ] Admin UI for viewing audit logs
- [ ] Retention policy documented

---

#### Week 12: Documentation and Polish
**Status:** Not Started  
**Effort:** 5 days  
**Priority:** LOW

**Objectives:**
- Create Architecture Decision Records (ADRs)
- Update README with current architecture
- Create developer onboarding guide
- Document deployment procedures
- Create contributing guidelines
- Review and update all javadoc
- Create diagrams (architecture, data model, flow)

**Documentation to Create:**

1. **Architecture Decision Records** (`docs/adr/`)
   - `001-use-quarkus-framework.md`
   - `002-panache-active-record-pattern.md`
   - `003-vaadin-flow-for-admin-ui.md`
   - `004-oidc-authentication.md`
   - `005-repository-pattern-extraction.md`

2. **Developer Guide** (`docs/DEVELOPER_GUIDE.md`)
   - Local development setup
   - Running tests
   - Database migrations
   - OIDC configuration for development
   - Debugging tips
   - Common troubleshooting

3. **API Documentation** (`docs/API.md`)
   - REST API overview
   - Authentication and authorization
   - Rate limiting (if applicable)
   - Error handling
   - Pagination
   - Examples and tutorials

4. **Deployment Guide** (`docs/DEPLOYMENT.md`)
   - Environment configuration
   - Database setup
   - OIDC provider configuration
   - Docker deployment
   - Kubernetes deployment (if applicable)
   - Monitoring and logging
   - Backup and recovery

5. **Contributing Guide** (`CONTRIBUTING.md`)
   - Code style guidelines
   - Branch naming conventions
   - Commit message format
   - Pull request process
   - Testing requirements
   - TDD workflow

6. **Architecture Diagrams** (`docs/diagrams/`)
   - System architecture diagram
   - Data model ERD
   - Authentication flow
   - Subject import flow
   - Email notification flow

**README Update:**

```markdown
# Elicit Admin Service

Administrative service for managing survey subjects, departments, and templates.

## Architecture

- **Framework:** Quarkus 3.30.2 (Java 21)
- **UI:** Vaadin Flow 24.9.6 (server-side Java UI)
- **Database:** PostgreSQL 17 with Flyway migrations
- **ORM:** Hibernate Panache (Active Record pattern)
- **Authentication:** OIDC (Keycloak)
- **API:** RESTful JSON API at `/api/v1/*`

## Quick Start

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven 3.8+

### Development Setup

1. Clone repository
2. Start PostgreSQL: `docker-compose up -d postgres`
3. Start Keycloak: `docker-compose up -d keycloak`
4. Run application: `./mvnw quarkus:dev`
5. Access UI: http://localhost:8080
6. Access API docs: http://localhost:8080/q/swagger-ui

### Running Tests

```bash
# All tests
./mvnw test

# With coverage
./mvnw verify

# Integration tests only
./mvnw verify -Pintegration-tests
```

## Project Structure

```
src/main/java/com/elicitsoftware/
├── admin/           # Vaadin UI views
├── model/           # JPA entities
├── repository/      # Data access layer
├── service/         # Business logic
├── resource/        # REST endpoints
├── security/        # Authentication & authorization
└── config/          # Application configuration
```

## Documentation

- [Developer Guide](docs/DEVELOPER_GUIDE.md)
- [API Documentation](docs/API.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Architecture Decisions](docs/adr/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Proprietary - Elicit Software
```

**Acceptance Criteria:**
- [ ] All ADRs created for major decisions
- [ ] README updated with current architecture
- [ ] Developer guide complete and tested
- [ ] API documentation complete
- [ ] Deployment guide complete
- [ ] Contributing guide created
- [ ] All diagrams created and up-to-date
- [ ] Javadoc reviewed and corrected
- [ ] Documentation reviewed by team

---

## Success Metrics

### Phase 1 Completion Criteria (CRITICAL)
- ✅ Test coverage ≥ 85% overall
- ✅ Test coverage ≥ 95% for critical services
- ✅ Zero `System.out` or `printStackTrace` in production code
- ✅ Repository pattern implemented for all entities
- ✅ Service interfaces created for all services

### Phase 2 Completion Criteria (HIGH)
- ✅ All classes ≤ 300 lines
- ✅ All methods ≤ 20 lines
- ✅ `RegisterView` split into focused components
- ✅ `CsvImportService` refactored with separation of concerns
- ✅ `TokenService` split into business logic and REST resource

### Phase 3 Completion Criteria (MEDIUM)
- ✅ All REST endpoints follow `/api/v1/*` convention
- ✅ OpenAPI documentation complete
- ✅ Zero TODO/FIXME comments
- ✅ Zero magic numbers or hard-coded strings
- ✅ Consistent error response structure

### Phase 4 Completion Criteria (LOW)
- ✅ Comprehensive audit logging implemented
- ✅ Complete documentation suite
- ✅ Architecture diagrams created
- ✅ Developer onboarding guide complete

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Breaking changes to REST API | Medium | High | Maintain legacy endpoints during transition |
| Test infrastructure delays | Low | High | Prioritize in Week 1, allocate extra time |
| Large refactoring introduces bugs | Medium | High | Incremental changes, comprehensive testing |
| Team capacity constraints | Medium | Medium | Phase 4 can be deferred if needed |
| OIDC configuration issues in tests | Medium | Medium | Use test doubles, mock identity |

---

## Dependencies and Prerequisites

### Required Before Starting
- [ ] Stakeholder buy-in for 12-week timeline
- [ ] Development environment access for all developers
- [ ] Test environment provisioned
- [ ] CI/CD pipeline configured for test execution
- [ ] Code freeze on new features during Phases 1-2

### External Dependencies
- JUnit 5, Mockito, AssertJ (testing)
- Testcontainers (integration tests)
- JaCoCo (coverage reporting)
- Checkstyle or SpotBugs (static analysis)

---

## Team Assignments (Example)

| Phase | Lead Developer | Supporting Developers | Estimated Duration |
|-------|---------------|----------------------|-------------------|
| Phase 1 | Developer A | Developer B | 4 weeks |
| Phase 2 | Developer B | Developer C | 3 weeks |
| Phase 3 | Developer C | Developer A | 3 weeks |
| Phase 4 | Developer A | All | 2 weeks |

---

## Progress Tracking

### Weekly Checkpoints
- **End of each week:** Review completed items
- **Blockers:** Document and escalate immediately
- **Coverage reports:** Generate and review weekly
- **Code reviews:** Required for all refactoring PRs

### Reporting
- Weekly status report to stakeholders
- Coverage dashboard updated daily
- Burndown chart for task completion

---

## Alternative Approaches Considered

### Approach 1: Big Bang Refactoring (REJECTED)
- **Pros:** Faster completion
- **Cons:** High risk, difficult to review, potential for bugs
- **Decision:** Rejected in favor of phased approach

### Approach 2: Only Critical Items (CONSIDERED)
- **Phases:** Only Phases 1-2 (7 weeks)
- **Pros:** Addresses mandatory requirements
- **Cons:** Leaves technical debt in REST API structure
- **Decision:** Available as fallback if timeline constraints arise

### Approach 3: Test-First for New Code Only (REJECTED)
- **Pros:** Requires less initial investment
- **Cons:** Doesn't address 0% current coverage
- **Decision:** Rejected - violates mandatory coverage requirements

---

## Questions for Stakeholders

1. **Timeline Flexibility:** Is 12-week timeline acceptable, or should we prioritize only Phases 1-2 (7 weeks)?

2. **Feature Freeze:** Can we freeze new feature development during Phases 1-2 (4 weeks) to focus on critical refactoring?

3. **REST API Breaking Changes:** Should we maintain backward compatibility with legacy endpoints, or coordinate a breaking change with clients?

4. **Test Coverage Targets:** Is 85% overall / 95% critical acceptable, or should we target higher?

5. **Resource Allocation:** Can we dedicate 2-3 developers full-time to this effort?

---

## Appendix A: .cursorrules Standards Summary

### Mandatory Requirements
- **TDD:** Write tests FIRST, then implementation
- **Coverage:** 85%+ overall, 95%+ for critical logic
- **Code Size:** Max 20 lines per method, max 300 lines per class
- **Logging:** SLF4J only, never System.out or printStackTrace
- **No Placeholders:** No TODO comments or "// Implementation here"
- **Complete Code:** Always generate complete, working code with imports

### Java Standards
- Java 17+ features (records, switch expressions, text blocks)
- Constructor injection over field injection
- Prefer immutability and Optional
- Parameter objects for methods with >3-4 parameters

### Security Requirements
- All endpoints require `@RolesAllowed` (except explicitly public)
- OIDC authentication
- Never log sensitive data
- Comprehensive audit logging
- Input validation and sanitization

### REST API Standards
- Plural nouns for collections
- Version APIs: `/api/v1/...`
- OpenAPI/Swagger documentation
- Consistent error responses
- Pagination for collections

### Database Standards
- Extend PanacheEntity/PanacheEntityBase
- Keep queries in repositories, not services
- Test with Testcontainers PostgreSQL

---

## Appendix B: File Reference Index

### Critical Files for Refactoring

**Week 1-2:**
- `src/main/java/com/elicitsoftware/service/EmailService.java`
- `src/main/java/com/elicitsoftware/service/TokenService.java`
- `src/main/java/com/elicitsoftware/service/CsvImportService.java`
- `src/main/java/com/elicitsoftware/report/ReportingService.java`
- `src/main/java/com/elicitsoftware/service/PDFService.java`
- `src/main/java/com/elicitsoftware/security/UiSessionLogin.java`

**Week 5:**
- `src/main/java/com/elicitsoftware/admin/flow/RegisterView.java`

**Week 6:**
- `src/main/java/com/elicitsoftware/service/CsvImportService.java`
- `src/main/java/com/elicitsoftware/service/TokenService.java`

**Week 9:**
- `src/main/java/com/elicitsoftware/model/Subject.java`
- `src/main/java/com/elicitsoftware/admin/flow/MainLayout.java`
- `src/main/java/com/elicitsoftware/admin/validator/RespondentValidator.java`

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-12-05 | AI Assistant | Initial plan creation |

---

**Next Steps:**
1. Review this plan with the development team
2. Get stakeholder approval for timeline and priorities
3. Address questions in "Questions for Stakeholders" section
4. Begin Phase 1, Week 1: Test Infrastructure Setup
5. Schedule weekly checkpoint meetings

---

*This document is a living plan and should be updated as the refactoring progresses.*
