package com.elicitsoftware.response;

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

import com.elicitsoftware.model.Status;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the status of an add response operation.
 * <p>
 * This class encapsulates the result of adding or importing data,
 * including the status and a descriptive import status message.
 * </p>
 */
public class AddResponseStatus  {

    @JsonProperty("status")
    private Status status;
    
    @JsonProperty("importStatus")
    private String importStatus;

    /**
     * Default constructor for AddResponseStatus.
     * <p>
     * Creates a new AddResponseStatus instance with default values.
     * Used primarily for JSON deserialization.
     * </p>
     */
    public AddResponseStatus() {
        // Default constructor for JSON deserialization
    }

    /**
     * Constructs an AddResponseStatus with the specified status and import status.
     *
     * @param status the Status object representing the operation status
     * @param importStatus a descriptive message about the import status
     */
    public AddResponseStatus(Status status, String importStatus) {
        this.status = status;
        this.importStatus = importStatus;
    }

    /**
     * Returns the status of this response.
     *
     * @return the Status object
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Sets the status of this response.
     *
     * @param status the Status object to set
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Returns the import status message.
     *
     * @return the import status message
     */
    public String getImportStatus() {
        return importStatus;
    }

    /**
     * Sets the import status message.
     *
     * @param importStatus the import status message to set
     */
    public void setImportStatus(String importStatus) {
        this.importStatus = importStatus;
    }

    /**
     * Returns a string representation of this AddResponseStatus object.
     * <p>
     * The string representation includes the status and import status fields,
     * providing a clear view of the current state of this response status.
     * <p>
     * This method is useful for logging, debugging, and general object inspection.
     *
     * @return A string representation of this AddResponseStatus
     */
    @Override
    public String toString() {
        return String.format("%s, \"%s\" }",
                importStatus != null ? importStatus : "null",
                status != null ? status.toString() : "null");
    }
    
}

