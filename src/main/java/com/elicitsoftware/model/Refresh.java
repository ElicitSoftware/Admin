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

/**
 * Refresh represents refresh status information for survey respondents.
 * <p>
 * This class holds status information related to refreshing or updating
 * respondent data, typically used for real-time updates and messaging
 * status tracking in the survey system.
 *
 * @author Elicit Software
 * @since 1.0.0
 */
public class Refresh {
    /**
     * External identifier for the respondent.
     * <p>
     * Unique identifier used to correlate this refresh status with
     * external systems or participant records.
     */
    public String xid;

    /**
     * Internal respondent database identifier.
     * <p>
     * The primary key of the respondent record in the database,
     * used for efficient internal lookups and relationships.
     */
    public long respondentId;

    /**
     * Current status of messaging for this respondent.
     * <p>
     * Indicates the state of message delivery or processing,
     * such as "sent", "delivered", "failed", or "pending".
     */
    public String messageStatus;

    /**
     * Constructs a new Refresh instance with the specified parameters.
     * <p>
     * Creates a refresh status record with the provided identifiers
     * and message status information.
     *
     * @param xid the external identifier for the respondent
     * @param respondentId the internal database ID of the respondent
     * @param messageStatus the current messaging status
     */
    public Refresh(String xid, long respondentId, String messageStatus) {
        this.xid = xid;
        this.respondentId = respondentId;
        this.messageStatus = messageStatus;
    }
}
