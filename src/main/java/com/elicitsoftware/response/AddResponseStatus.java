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

public class AddResponseStatus  {

    @JsonProperty("status")
    private Status status;
    
    @JsonProperty("importStatus")
    private String importStatus;

    public AddResponseStatus() {
        // Default constructor for JSON deserialization
    }

    public AddResponseStatus(Status status, String importStatus) {
        this.status = status;
        this.importStatus = importStatus;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getImportStatus() {
        return importStatus;
    }

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

