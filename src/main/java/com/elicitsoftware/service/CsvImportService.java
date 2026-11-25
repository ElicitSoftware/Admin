package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Admin
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.admin.i18n.Translations;
import com.elicitsoftware.request.AddRequest;
import com.elicitsoftware.response.AddResponse;
import com.elicitsoftware.rest.AccessCodeService;
import io.quarkus.logging.Log;
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
 * surveyId,departmentId,xid,firstName,lastName,middleName,dob,email,phone
 * </pre>
 * <p>
 * Field descriptions:
 * - **surveyId**: Integer ID of the survey the row is registered onto (required)
 * - **departmentId**: Integer ID of department (must be accessible by importing user)
 * - **xid**: Optional external identifier for cross-system tracking
 * - **firstName**: Required participant first name
 * - **lastName**: Required participant last name
 * - **middleName**: Optional middle name (may be empty)
 * - **dob**: Date of birth in yyyy-MM-dd or MM/dd/yyyy format
 * - **email**: Required email address
 * - **phone**: Optional phone number
 * <p>
 * CSV Format Features:
 * - Quoted fields supported for values containing commas
 * - Comment lines starting with '#' are ignored
 * - Empty lines are skipped
 * - Field validation with descriptive error messages
 * - Minimum 8 fields required (surveyId through email)
 * <p>
 * Security and Validation:
 * - Department ID must be accessible by the importing user
 * - Survey ID is read from the first column of every row (BR-175)
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
 * @see AccessCodeService
 * @see com.elicitsoftware.model.User
 */
@ApplicationScoped
public class CsvImportService {

    /**
     * Access code service for participant registration and access code generation.
     * <p>
     * This service handles the actual participant creation in the database
     * and generates access codes for survey access.
     */
    private final AccessCodeService accessCodeService;

    /**
     * Constructs a new CsvImportService with the specified AccessCodeService dependency.
     * <p>
     * The AccessCodeService is injected to handle participant registration operations
     * that occur during the CSV import process.
     *
     * @param accessCodeService The service responsible for participant registration and access code generation
     */
    public CsvImportService(AccessCodeService accessCodeService) {
        this.accessCodeService = accessCodeService;
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
     * 6. **Create participant records** via AccessCodeService
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
     * - Every row names its survey in the first column; one that does not rejects the file (UC-010 BR-178)
     * - Input validation prevents injection attacks
     * - Transaction rollback prevents partial imports
     *
     * @param csvInputStream The input stream containing CSV data to import
     *                       //     * @param user The user performing the import (used for department permission validation)
     * @return The number of successfully imported participants
     * @throws CsvImportException If any line was rejected; it carries one message per rejected
     *                            line as well as the aggregated message
     * @throws Exception If the file itself cannot be read
     *                   //     * @see #parseCsvLine(String, User)
     * @see #plan(InputStream)
     * @see #splitCsvLine(String)
     * @see AccessCodeService#registerSubject(AddRequest)
     */
    @Transactional
    public AddResponse importSubjects(InputStream csvInputStream) throws Exception {
        CsvImportPlan plan = plan(csvInputStream);
        // UC-010 BR-178: a row without its survey rejects the whole upload, before anyone is
        // registered.
        if (plan.rejected()) {
            throw new CsvSurveyMissingException(plan.linesWithoutSurvey());
        }
        return importAccepted(plan);
    }

    /**
     * Reads a CSV file and decides, for every row, what an import would do with it, without
     * registering anyone (UC-003 A6 step 2, BR-176): the rows it would register, the rows it
     * would refuse and why, and the rows that name no survey, any one of which rejects the file
     * (UC-003 BR-175, UC-010 BR-178).
     *
     * @param csvInputStream the uploaded file
     * @return what an import of this file would do
     * @throws Exception if the file itself cannot be read
     */
    @Transactional
    public CsvImportPlan plan(InputStream csvInputStream) throws Exception {
        CsvImportPlan plan = new CsvImportPlan();
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Comment lines and empty lines are skipped.
                if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                String[] fields = splitCsvLine(line);
                if (fields.length <= SURVEY_COLUMN || fields[SURVEY_COLUMN].trim().isEmpty()) {
                    plan.withoutSurvey.add(lineNumber);
                    continue;
                }
                try {
                    // parseCsvLine only ever throws user-actionable validation messages
                    // (missing/invalid field values), which are safe to surface as-is.
                    AddRequest request = parseCsvLine(fields);
                    // UC-010 BR-137: a line for a department the caller is not assigned to
                    // is refused by its number; the other lines go on.
                    if (!accessCodeService.permitsDepartment(request.departmentId)) {
                        throw new Exception(Translations.get("csvImport.departmentNotPermitted", String.valueOf(request.departmentId)));
                    }
                    // UC-006 BR-172, UC-010 BR-177: and so is a line whose department does not
                    // register onto its survey, or whose survey is not installed.
                    if (!accessCodeService.offersSurvey(request.departmentId, request.surveyId)) {
                        throw new Exception(Translations.get("csvImport.surveyNotOffered",
                                String.valueOf(request.departmentId), String.valueOf(request.surveyId)));
                    }
                    // UC-010 BR-188: a line whose subject breaks a rule is refused here, before
                    // anyone is registered, so it cannot fail the import's transaction later.
                    List<String> faults = accessCodeService.subjectRuleViolations(request);
                    if (!faults.isEmpty()) {
                        throw new Exception(String.join("; ", faults));
                    }
                    plan.accepted.add(new CsvRow(lineNumber, request));
                } catch (Exception e) {
                    plan.refused.add(Translations.get("definition.line", String.valueOf(lineNumber), e.getMessage()));
                }
            }
        }
        return plan;
    }

    /**
     * Registers the rows a plan accepted (UC-003 A6 step 4, UC-010). A row that fails
     * unexpectedly is reported by its line and the others go on.
     *
     * @param plan what {@link #plan(InputStream)} decided
     * @return the outcome of each row registered
     * @throws CsvImportException if the plan refused any row, or a row failed; the rows that were
     *                            accepted are registered either way
     */
    @Transactional
    public AddResponse importAccepted(CsvImportPlan plan) throws CsvImportException {
        AddResponse response = new AddResponse();
        List<String> errors = new ArrayList<>(plan.refused);
        for (CsvRow row : plan.accepted) {
            try {
                AddResponse subjectResponse = accessCodeService.registerSubject(row.request());
                if (subjectResponse.getErrors().size() > 0) {
                    response.setError(subjectResponse.getErrors().get(0));
                } else {
                    response.addStatus(subjectResponse.getSubjects().getFirst());
                }
            } catch (Exception e) {
                // Unlike parseCsvLine, registerSubject can propagate an unexpected
                // infra/persistence failure - log it and keep the client-facing
                // message generic rather than leaking driver/JPA error text.
                Log.errorf(e, "CSV import: unexpected error processing line %d", row.line());
                errors.add(Translations.get("definition.line", String.valueOf(row.line()), Translations.get("csvImport.unexpected")));
            }
        }
        if (!errors.isEmpty()) {
            throw new CsvImportException(errors);
        }
        return response;
    }

    /** The first column: the survey a row is registered onto (BR-175). */
    static final int SURVEY_COLUMN = 0;
    static final int DEPARTMENT_COLUMN = 1;
    static final int XID_COLUMN = 2;
    static final int FIRST_NAME_COLUMN = 3;
    static final int LAST_NAME_COLUMN = 4;
    static final int MIDDLE_NAME_COLUMN = 5;
    static final int DOB_COLUMN = 6;
    static final int EMAIL_COLUMN = 7;
    static final int PHONE_COLUMN = 8;

    /**
     * One row an import would register, with the line it came from.
     *
     * @param line    the row's line number in the file
     * @param request the registration it asks for
     */
    public record CsvRow(int line, AddRequest request) {
    }

    /** What an import of one file would do, row by row (UC-003 A6 step 2). */
    public static final class CsvImportPlan {
        final List<CsvRow> accepted = new ArrayList<>();
        final List<String> refused = new ArrayList<>();
        final List<Integer> withoutSurvey = new ArrayList<>();

        /**
         * Returns the rows that would be registered.
         *
         * @return the rows that would be registered, as an unmodifiable copy
         */
        public List<CsvRow> accepted() {
            return List.copyOf(accepted);
        }

        /**
         * Returns the rows that would be refused.
         *
         * @return the rows that would be refused, each "Line n: reason", as an unmodifiable copy
         */
        public List<String> refused() {
            return List.copyOf(refused);
        }

        /**
         * Returns the line numbers of the rows that name no survey.
         *
         * @return the line numbers of the rows that name no survey, as an unmodifiable copy
         */
        public List<Integer> linesWithoutSurvey() {
            return List.copyOf(withoutSurvey);
        }

        /**
         * Returns whether the whole file is rejected.
         *
         * @return true when a row names no survey, which rejects the whole file (BR-175)
         */
        public boolean rejected() {
            return !withoutSurvey.isEmpty();
        }
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
     * - **Survey ID**: The first column (UC-003 BR-175, UC-010 BR-178)
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
     * 0        | surveyId     | Yes      | Integer (presence checked before this method, by plan)
     * 1        | departmentId | Yes      | Integer
     * 2        | xid          | No       | String
     * 3        | firstName    | Yes      | Non-empty string
     * 4        | lastName     | Yes      | Non-empty string
     * 5        | middleName   | No       | String (may be empty)
     * 6        | dob          | No       | yyyy-MM-dd or MM/dd/yyyy
     * 7        | email        | Yes      | Non-empty string
     * 8        | phone        | No       | String
     * </pre>
     *
     * @param fields The CSV line's fields, split by splitCsvLine
     * @return AddRequest object populated with validated participant data
     * @throws Exception If validation fails with descriptive error message indicating the specific problem
     * @see #splitCsvLine(String)
     * @see AddRequest
     */
    private AddRequest parseCsvLine(String[] fields) throws Exception {

        if (fields.length <= EMAIL_COLUMN) {
            throw new Exception(Translations.get("csvImport.badFormat"));
        }

        AddRequest request = new AddRequest();

        // UC-003 BR-175, UC-010 BR-178: the first column names the survey.
        try {
            request.surveyId = Integer.parseInt(fields[SURVEY_COLUMN].trim());
        } catch (NumberFormatException e) {
            throw new Exception(Translations.get("csvImport.badSurveyId"));
        }

        try {
            // Parse department ID
            request.departmentId = Integer.parseInt(fields[DEPARTMENT_COLUMN].trim());

            // Parse external ID
            request.xid = fields[XID_COLUMN].trim();

            // Parse names
            request.firstName = fields[FIRST_NAME_COLUMN].trim();
            request.lastName = fields[LAST_NAME_COLUMN].trim();
            request.middleName = fields[MIDDLE_NAME_COLUMN].trim();

            // Parse date of birth
            String dob = fields[DOB_COLUMN].trim();
            if (!dob.isEmpty()) {
                try {
                    request.dob = LocalDate.parse(dob, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (DateTimeParseException e) {
                    try {
                        request.dob = LocalDate.parse(dob, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    } catch (DateTimeParseException e2) {
                        throw new Exception(Translations.get("csvImport.badDate"));
                    }
                }
            }

            // Parse email
            request.email = fields[EMAIL_COLUMN].trim();

            // Parse phone
            request.phone = fields.length > PHONE_COLUMN ? fields[PHONE_COLUMN].trim() : null;

            // Validate required fields
            if (request.firstName.isEmpty()) {
                throw new Exception(Translations.get("csvImport.firstNameRequired"));
            }
            if (request.lastName.isEmpty()) {
                throw new Exception(Translations.get("csvImport.lastNameRequired"));
            }
            if (request.email.isEmpty()) {
                throw new Exception(Translations.get("csvImport.emailRequired"));
            }

        } catch (NumberFormatException e) {
            throw new Exception(Translations.get("csvImport.badDepartmentId"));
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
     * @see #plan(InputStream)
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
