package com.elicitsoftware.model;

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

import java.util.List;

/**
 * Data transfer object for paginated Subject/respondent list responses in the Elicit system.
 * 
 * <p>This class provides a standardized response format for API endpoints that return
 * lists of survey subjects/respondents along with pagination information. It combines
 * the actual data list with metadata about the total length for client-side pagination
 * and display purposes.</p>
 * 
 * <p><strong>Use Cases:</strong></p>
 * <ul>
 *   <li><strong>API Responses:</strong> RESTful endpoints returning subject lists</li>
 *   <li><strong>Pagination Support:</strong> Provides total count for pagination calculations</li>
 *   <li><strong>Search Results:</strong> Returns filtered subject lists with result counts</li>
 *   <li><strong>Data Export:</strong> Structured format for data transfer operations</li>
 * </ul>
 * 
 * <p><strong>Response Structure:</strong></p>
 * <ul>
 *   <li><strong>Respondents:</strong> List of Subject entities matching the query</li>
 *   <li><strong>Length:</strong> Total count of matching records (for pagination)</li>
 * </ul>
 * 
 * <p><strong>Pagination Pattern:</strong></p>
 * <pre>{@code
 * // API endpoint example
 * @GET
 * @Path("/subjects")
 * public ListResponse getSubjects(@QueryParam("page") int page, 
 *                                @QueryParam("size") int size) {
 *     List<Subject> subjects = Subject.find("ORDER BY createdDt DESC")
 *                                    .page(page, size)
 *                                    .list();
 *     long totalCount = Subject.count();
 *     return new ListResponse(subjects, totalCount);
 * }
 * 
 * // Client-side pagination calculation
 * ListResponse response = getSubjects(0, 20);
 * int totalPages = (int) Math.ceil((double) response.getLength() / 20);
 * boolean hasMore = response.getRespondents().size() == 20;
 * }</pre>
 * 
 * <p><strong>JSON Serialization:</strong></p>
 * <pre>{@code
 * {
 *   "respondents": [
 *     { "id": 1, "firstName": "John", "lastName": "Doe", ... },
 *     { "id": 2, "firstName": "Jane", "lastName": "Smith", ... }
 *   ],
 *   "length": 150
 * }
 * }</pre>
 * 
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see Subject
 */
public class ListResponse {

    /**
     * List of Subject entities returned in this response.
     * 
     * <p>Contains the actual Subject (respondent) data that matches the request
     * criteria. This list may be a subset of the total matching records when
     * pagination is applied.</p>
     */
    private List<Subject> respondents = null;
    
    /**
     * Total number of Subject entities that match the query criteria.
     * 
     * <p>Represents the total count of records that match the search/filter
     * criteria, regardless of pagination limits. This value is essential
     * for client-side pagination calculations and displaying total result
     * counts to users.</p>
     */
    private long length;

    /**
     * Constructs a new ListResponse with the specified subjects and total count.
     * 
     * <p>Creates a complete response object containing both the filtered/paginated
     * list of subjects and the total count for pagination purposes.</p>
     * 
     * @param respondents the list of Subject entities to include in the response
     * @param length the total number of matching records (for pagination)
     */
    public ListResponse(List<Subject> respondents, long length) {
        this.respondents = respondents;
        this.length = length;
    }

    /**
     * Gets the list of respondents (Subject entities) in this response.
     * 
     * @return the list of Subject entities, may be null if not set
     */
    public List<Subject> getRespondents() {
        return respondents;
    }

    /**
     * Sets the list of respondents (Subject entities) for this response.
     * 
     * @param respondents the list of Subject entities to set
     */
    public void setRespondents(List<Subject> respondents) {
        this.respondents = respondents;
    }

    /**
     * Gets the total number of matching records.
     * 
     * @return the total count of records matching the query criteria
     */
    public long getLength() {
        return length;
    }

    /**
     * Sets the total number of matching records.
     * 
     * @param length the total count to set
     */
    public void setLength(long length) {
        this.length = length;
    }
}
