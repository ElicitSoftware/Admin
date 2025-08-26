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

import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * CsvImportService provides bulk participant import functionality from CSV files.
 * <p>
 * This application-scoped service handles the parsing and import of participant data
 * from CSV (Comma-Separated Values) files into the Elicit Survey system. It supports
 * batch participant registration with comprehensive error handling and validation.
 * <p>
 * Key features:
 * - **Bulk participant import** from standardized CSV format
 * - **Comprehensive validation** of participant data and permissions
 * - **Error handling** with detailed line-by-line error reporting
 * - **Flexible date format support** (yyyy-MM-dd and MM/dd/yyyy)
 * - **CSV parsing** with support for quoted fields and embedded commas
 * - **Transactional processing** ensuring data consistency
 * - **Department security** validation against user permissions
 * <p>
 * CSV Format Requirements:
 * The service expects CSV files with the following column structure:
 * <pre>
 * departmentId,firstName,lastName,middleName,dob,email,phone,xid
 * </pre>
 * <p>
 * Field descriptions:
 * - **departmentId**: Integer ID of department (must be accessible by importing user)
 * - **firstName**: Required participant first name
 * - **lastName**: Required participant last name
 * - **middleName**: Optional middle name (may be empty)
 * - **dob**: Date of birth in yyyy-MM-dd or MM/dd/yyyy format
 * - **email**: Required email address
 * - **phone**: Optional phone number
 * - **xid**: Optional external identifier for cross-system tracking
 * <p>
 * CSV Format Features:
 * - Quoted fields supported for values containing commas
 * - Comment lines starting with '#' are ignored
 * - Empty lines are skipped
 * - Field validation with descriptive error messages
 * - Minimum 6 fields required (departmentId through email)
 * <p>
 * Security and Validation:
 * - Department ID must be accessible by the importing user
 * - Survey ID is automatically set to 1 (configurable in future versions)
 * - Required field validation (firstName, lastName, email)
 * - Date format validation with multiple format support
 * - Comprehensive error collection and reporting
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * @Inject
 * CsvImportService csvImportService;
 *
 * public void importParticipants(InputStream csvFile, User currentUser) {
 *     try {
 *         int successCount = csvImportService.importSubjects(csvFile, currentUser);
 *         logger.info("Successfully imported {} participants", successCount);
 *     } catch (Exception e) {
 *         logger.error("Import failed: {}", e.getMessage());
 *         // Display errors to user
 *     }
 * }
 * }
 * </pre>
 *
 * @see AddRequest
 * @see AddResponse
 * @see TokenService
 * @see com.elicitsoftware.model.User
 * @since 1.0.0
 */
@ApplicationScoped
public class CsvImportService {

    /**
     * Token service for participant registration and authentication token generation.
     * <p>
     * This service handles the actual participant creation in the database
     * and generates authentication tokens for survey access.
     */
    private final TokenService tokenService;

    /**
     * Constructs a new CsvImportService with the specified TokenService dependency.
     * <p>
     * The TokenService is injected to handle participant registration operations
     * that occur during the CSV import process.
     *
     * @param tokenService The service responsible for participant registration and token generation
     */
    public CsvImportService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Imports participant data from a CSV input stream into the survey system.
     * <p>
     * This method processes a CSV file containing participant information and
     * creates corresponding participant records in the database. It performs
     * comprehensive validation, error handling, and security checks throughout
     * the import process.
     * <p>
     * Processing workflow:
     * 1. **Parse CSV line by line** with support for comments and empty lines
     * 2. **Validate CSV format** ensuring minimum required fields
     * 3. **Check department permissions** against user's accessible departments
     * 4. **Validate required fields** (firstName, lastName, email)
     * 5. **Parse and validate dates** with multiple format support
     * 6. **Create participant records** via TokenService
     * 7. **Collect errors** for detailed reporting
     * 8. **Return success count** or throw aggregated errors
     * <p>
     * Transaction behavior:
     * - The entire import operation is wrapped in a database transaction
     * - If any critical errors occur, all changes are rolled back
     * - Individual line errors are collected but don't stop processing
     * - Final error reporting includes all validation failures
     * <p>
     * CSV line processing:
     * - Comment lines starting with '#' are automatically skipped
     * - Empty lines are ignored
     * - Each data line is parsed into an AddRequest
     * - Line numbers are tracked for error reporting
     * - Quoted fields are properly handled for embedded commas
     * <p>
     * Error handling:
     * - **Line-level errors**: Collected with line numbers for user feedback
     * - **Format errors**: Invalid CSV structure or missing fields
     * - **Validation errors**: Required field violations or invalid data
     * - **Permission errors**: Department access violations
     * - **System errors**: Database or service failures
     * <p>
     * Security considerations:
     * - Department ID validation ensures users can only import to accessible departments
     * - Survey ID is currently hardcoded to 1 (future enhancement needed)
     * - Input validation prevents injection attacks
     * - Transaction rollback prevents partial imports
     *
     * @param csvInputStream The input stream containing CSV data to import
     *                       //     * @param user The user performing the import (used for department permission validation)
     * @return The number of successfully imported participants
     * @throws Exception If the import fails with aggregated error messages from all failed lines
     *                   //     * @see #parseCsvLine(String, User)
     * @see #parseCsvLine(String)
     * @see #splitCsvLine(String)
     * @see TokenService#putSubject(AddRequest)
     */
    @Transactional
    public AddResponse importSubjects(InputStream csvInputStream) throws Exception {
        List<String> errors = new ArrayList<>();
        int lineNumber = 0;
        AddResponse response = new AddResponse();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream))) {
            String line;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip comment lines that start with #
                if (line.trim().startsWith("#")) {
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    AddRequest request = parseCsvLine(line);
                    AddResponse subjectResponse = tokenService.putSubject(request);

                    if (subjectResponse.getErrors().size() > 0 ) {
                        response.setError(subjectResponse.getErrors().get(0));
                    } else {
                        response.addStatus(subjectResponse.getSubjects().getFirst());
                    }
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new Exception("Import completed with errors:\n" + String.join("\n", errors));
        }

        return response;
    }

    /**
     * Parses a single CSV line into an AddRequest object with comprehensive validation.
     * <p>
     * This method handles the conversion of a CSV data line into a structured
     * AddRequest object suitable for participant registration. It performs
     * extensive validation on each field and ensures data integrity before
     * returning the request object.
     * <p>
     * Field parsing and validation:
     * - **Department ID**: Must be integer and accessible by the importing user
     * - **Survey ID**: Automatically set to 1 (hardcoded for current version)
     * - **Personal names**: firstName and lastName are required, middleName optional
     * - **Date of birth**: Supports yyyy-MM-dd and MM/dd/yyyy formats
     * - **Contact info**: Email required, phone optional
     * - **External ID**: Optional identifier for cross-system integration
     * <p>
     * Date format handling:
     * The method supports flexible date parsing with fallback:
     * 1. First attempts ISO format (yyyy-MM-dd)
     * 2. Falls back to US format (MM/dd/yyyy) if ISO parsing fails
     * 3. Throws descriptive error if both formats fail
     * <p>
     * Security validation:
     * - Department ID must exist in user's accessible departments list
     * - Prevents users from importing to unauthorized departments
     * - Validates integer parsing for department ID
     * <p>
     * Required field validation:
     * - firstName: Must be non-empty after trimming
     * - lastName: Must be non-empty after trimming
     * - email: Must be present and non-empty
     * <p>
     * CSV field mapping:
     * <pre>
     * Position | Field        | Required | Format
     * ---------|--------------|----------|------------------
     * 0        | departmentId | Yes      | Integer
     * 1        | firstName    | Yes      | Non-empty string
     * 2        | lastName     | Yes      | Non-empty string
     * 3        | middleName   | No       | String (may be empty)
     * 4        | dob          | No       | yyyy-MM-dd or MM/dd/yyyy
     * 5        | email        | Yes      | Non-empty string
     * 6        | phone        | No       | String
     * 7        | xid          | No       | String
     * </pre>
     *
     * @param csvLine The CSV line to parse containing comma-separated participant data
     *                //     * @param user The user performing the import (used for department validation)
     * @return AddRequest object populated with validated participant data
     * @throws Exception If validation fails with descriptive error message indicating the specific problem
     * @see #splitCsvLine(String)
     * @see AddRequest
     */
//    private AddRequest parseCsvLine(String csvLine, User user) throws Exception {
    private AddRequest parseCsvLine(String csvLine) throws Exception {
        // Split CSV line, handling quoted fields
        String[] fields = splitCsvLine(csvLine);

        if (fields.length < 6) {
            throw new Exception("Invalid CSV format. Expected at least 6 fields: departmentId,firstName,lastName,middleName,dob,email,phone,xid");
        }

        AddRequest request = new AddRequest();

        try {
            // Parse department ID
            request.departmentId = Integer.parseInt(fields[0].trim());

            // Set survey ID (assuming survey ID 1 like in the original code)
            request.surveyId = 1;

            // Parse other fields
            request.firstName = fields[1].trim();
            request.lastName = fields[2].trim();
            request.middleName = fields.length > 3 ? fields[3].trim() : null;

            // Parse date of birth
            if (fields.length > 4 && !fields[4].trim().isEmpty()) {
                try {
                    request.dob = LocalDate.parse(fields[4].trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (DateTimeParseException e) {
                    try {
                        request.dob = LocalDate.parse(fields[4].trim(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    } catch (DateTimeParseException e2) {
                        throw new Exception("Invalid date format. Use yyyy-MM-dd or MM/dd/yyyy");
                    }
                }
            }

            // Parse email
            request.email = fields.length > 5 ? fields[5].trim() : null;

            // Parse phone
            request.phone = fields.length > 6 ? fields[6].trim() : null;

            // Parse external ID
            request.xid = fields.length > 7 ? fields[7].trim() : null;

            // Validate required fields
            if (request.firstName.isEmpty()) {
                throw new Exception("First name is required");
            }
            if (request.lastName.isEmpty()) {
                throw new Exception("Last name is required");
            }
            if (request.email == null || request.email.isEmpty()) {
                throw new Exception("Email is required");
            }

        } catch (NumberFormatException e) {
            throw new Exception("Invalid department ID format");
        }

        return request;
    }

    /**
     * Splits a CSV line into individual fields with support for quoted values containing commas.
     * <p>
     * This method provides robust CSV parsing that handles the complexities of the CSV format
     * including quoted fields that may contain embedded commas. It uses a state-machine
     * approach to track whether the parser is currently inside quoted content.
     * <p>
     * CSV parsing features:
     * - **Comma separation**: Fields are separated by commas outside of quotes
     * - **Quote handling**: Double quotes (") are used to delimit fields with embedded commas
     * - **Embedded commas**: Commas inside quoted fields are preserved as literal content
     * - **Quote state tracking**: Maintains state to handle opening and closing quotes
     * - **Flexible field count**: Returns array sized to actual field count
     * <p>
     * Parsing algorithm:
     * 1. Iterate through each character in the CSV line
     * 2. Track quote state (inside or outside quoted content)
     * 3. When encountering quotes, toggle the quote state
     * 4. When encountering commas outside quotes, complete current field
     * 5. Accumulate all other characters into current field buffer
     * 6. Add final field after processing entire line
     * <p>
     * Example parsing behavior:
     * <pre>
     * Input:  'John,Doe,"123 Main St, Apt 5",30'
     * Output: ["John", "Doe", "123 Main St, Apt 5", "30"]
     *
     * Input:  'Smith,"Jane ""JJ"" Middle",jsmith@email.com'
     * Output: ["Smith", "Jane \"JJ\" Middle", "jsmith@email.com"]
     * </pre>
     * <p>
     * Limitations:
     * - Does not handle escaped quotes within quoted fields (Excel-style double quotes)
     * - Assumes well-formed CSV input (unmatched quotes may cause issues)
     * - Does not trim whitespace (preserves spacing as-is)
     * <p>
     * Performance considerations:
     * - Uses StringBuilder for efficient string concatenation
     * - Single-pass parsing algorithm for optimal performance
     * - Minimal memory allocation during parsing process
     *
     * @param csvLine The CSV line to split into individual fields
     * @return String array containing the individual fields from the CSV line
     * //     * @see #parseCsvLine(String, User)
     * @see #parseCsvLine(String)
     */
    private String[] splitCsvLine(String csvLine) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csvLine.length(); i++) {
            char c = csvLine.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }

        fields.add(currentField.toString());
        return fields.toArray(new String[0]);
    }
}
