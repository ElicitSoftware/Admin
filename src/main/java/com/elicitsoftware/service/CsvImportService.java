package com.elicitsoftware.service;

import com.elicitsoftware.model.User;
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

@ApplicationScoped
public class CsvImportService {

    private final TokenService tokenService;

    public CsvImportService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Transactional
    public int importSubjects(InputStream csvInputStream, User user) throws Exception {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int lineNumber = 0;

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
                    AddRequest request = parseCsvLine(line, user);
                    AddResponse response = tokenService.putSubject(request);

                    if (response.getError() != null) {
                        errors.add("Line " + lineNumber + ": " + response.getError());
                    } else {
                        successCount++;
                    }
                } catch (Exception e) {
                    errors.add("Line " + lineNumber + ": " + e.getMessage());
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new Exception("Import completed with errors:\n" + String.join("\n", errors));
        }

        return successCount;
    }

    private AddRequest parseCsvLine(String csvLine, User user) throws Exception {
        // Split CSV line, handling quoted fields
        String[] fields = splitCsvLine(csvLine);

        if (fields.length < 6) {
            throw new Exception("Invalid CSV format. Expected at least 6 fields: departmentId,firstName,lastName,middleName,dob,email,phone,xid");
        }

        AddRequest request = new AddRequest();

        try {
            // Parse department ID
            request.departmentId = Integer.parseInt(fields[0].trim());

            // Validate department belongs to user
            boolean validDepartment = user.getDepartments().stream()
                    .anyMatch(dept -> dept.id == request.departmentId);

            if (!validDepartment) {
                throw new Exception("Invalid department ID: " + request.departmentId);
            }

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