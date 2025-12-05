package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.integration.PostgresTestResource;
import com.elicitsoftware.model.Department;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TokenService.
 * <p>
 * Tests the core functionality of token generation and subject management,
 * including subject creation, duplicate handling, and exclusion logic.
 * 
 * @author Elicit Software
 * @since 1.0.0
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@DisplayName("TokenService Tests")
class TokenServiceTest {

    @Inject
    TokenService tokenService;

    private Department testDepartment;
    private Survey testSurvey;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up existing test data
        cleanupTestData();
        
        // Create test department
        testDepartment = new Department();
        testDepartment.name = "Test Department";
        testDepartment.persist();
        
        // Create test survey
        testSurvey = new Survey();
        testSurvey.name = "Test Survey";
        testSurvey.persist();
    }

    @Transactional
    void cleanupTestData() {
        // Clean up in reverse order of dependencies
        // This would need to be expanded based on actual schema
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"elicit_admin"})
    @DisplayName("Should create new subject with valid request")
    void shouldCreateNewSubjectWithValidRequest() {
        // Given
        AddRequest request = createValidAddRequest();
        
        // When
        AddResponse response = tokenService.putSubject(request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getSubjects()).isNotEmpty();
        assertThat(response.getSubjects().get(0).getImportStatus()).contains("New Subject");
        assertThat(response.getSubjects().get(0).getStatus()).isNotNull();
        assertThat(response.getSubjects().get(0).getStatus().getToken()).isNotEmpty();
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"elicit_admin"})
    @DisplayName("Should return existing subject when duplicate XID submitted")
    void shouldReturnExistingSubjectWhenDuplicateXidSubmitted() {
        // Given
        AddRequest request = createValidAddRequest();
        tokenService.putSubject(request);
        
        // When - submit same request again
        AddResponse response = tokenService.putSubject(request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getSubjects()).isNotEmpty();
        assertThat(response.getSubjects().get(0).getImportStatus()).contains("Existing Subject");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"elicit_admin"})
    @DisplayName("Should handle multiple subjects in batch")
    void shouldHandleMultipleSubjectsInBatch() {
        // Given
        AddRequest request1 = createValidAddRequest();
        request1.xid = "TEST-001";
        
        AddRequest request2 = createValidAddRequest();
        request2.xid = "TEST-002";
        
        // When
        AddResponse response = tokenService.putSubjects(java.util.List.of(request1, request2));
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getSubjects()).hasSize(2);
        assertThat(response.getSubjects().get(0).getImportStatus()).contains("New Subject");
        assertThat(response.getSubjects().get(1).getImportStatus()).contains("New Subject");
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"elicit_admin"})
    @DisplayName("Should generate unique tokens for different subjects")
    void shouldGenerateUniqueTokensForDifferentSubjects() {
        // Given
        AddRequest request1 = createValidAddRequest();
        request1.xid = "TEST-UNIQUE-001";
        
        AddRequest request2 = createValidAddRequest();
        request2.xid = "TEST-UNIQUE-002";
        
        // When
        AddResponse response1 = tokenService.putSubject(request1);
        AddResponse response2 = tokenService.putSubject(request2);
        
        // Then
        String token1 = response1.getSubjects().get(0).getStatus().getToken();
        String token2 = response2.getSubjects().get(0).getStatus().getToken();
        
        assertThat(token1).isNotEmpty();
        assertThat(token2).isNotEmpty();
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @TestSecurity(user = "testuser", roles = {"elicit_admin"})
    @DisplayName("Should validate token format is readable")
    void shouldValidateTokenFormatIsReadable() {
        // Given
        AddRequest request = createValidAddRequest();
        
        // When
        AddResponse response = tokenService.putSubject(request);
        
        // Then
        String token = response.getSubjects().get(0).getStatus().getToken();
        assertThat(token).hasSize(9);
        // Should not contain easily confused characters (0, O, 1, l, I)
        assertThat(token).doesNotContain("0", "O", "1", "l", "I");
    }

    /**
     * Creates a valid AddRequest for testing.
     */
    private AddRequest createValidAddRequest() {
        AddRequest request = new AddRequest();
        request.surveyId = (int) testSurvey.id;
        request.xid = "TEST-XID-" + System.currentTimeMillis();
        request.departmentId = (int) testDepartment.id;
        request.firstName = "John";
        request.lastName = "Doe";
        request.middleName = "A";
        request.email = "john.doe@test.com";
        request.phone = "555-123-4567";
        request.dob = LocalDate.of(1990, 1, 1);
        return request;
    }
}
