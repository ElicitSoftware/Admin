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

import com.elicitsoftware.model.Status;
import io.quarkus.panache.common.Page;

import java.util.List;

/**
 * StatusDataSource provides data access for {@link Status} entities with database-level
 * pagination and injection-safe, parameterized filtering.
 *
 * <p>This service class acts as a data access layer between the UI components and the
 * underlying Panache ORM. Every query is described by an immutable {@link StatusQuery} that
 * keeps user-supplied values in a named-parameter map, so filter values can never alter the
 * query structure (no HQL injection). Paging and counting are pushed down to the database via
 * Panache's {@link io.quarkus.panache.common.Page} / {@code count} support rather than being
 * performed in memory.</p>
 *
 * @author Elicit Software
 * @version 1.0
 * @see Status
 * @see StatusQuery
 */
public class StatusDataSource {

    /**
     * Default constructor.
     *
     * <p>Creates a new StatusDataSource instance ready for data access operations. The data
     * source is stateless and thread-safe.</p>
     */
    public StatusDataSource() {
        // Default constructor for data source initialization
    }

    /**
     * Fetches a single page of {@link Status} entities matching the given parameterized query.
     *
     * <p>Paging is performed at the database level: only the requested window of rows is
     * transferred from the database, regardless of how large the full result set is. All filter
     * values are bound as named parameters, so they cannot alter the query structure.</p>
     *
     * @param query  the parameterized query describing the filter and ordering; must not be {@code null}
     * @param offset the number of records to skip from the beginning of the results (0-based)
     * @param limit  the maximum number of records to return in this page (must be positive)
     * @return the list of matching {@link Status} entities for the requested page
     * @see #count(StatusQuery)
     */
    public List<Status> fetch(StatusQuery query, int offset, int limit) {
        int effectiveLimit = Math.max(limit, 1);
        int pageIndex = offset / effectiveLimit;
        var find = query.sort() != null
                ? Status.find(query.whereClause(), query.sort(), query.params())
                : Status.find(query.whereClause(), query.params());
        return find.page(Page.of(pageIndex, effectiveLimit)).list();
    }

    /**
     * Counts the total number of {@link Status} entities matching the given parameterized query.
     *
     * <p>The count is computed by the database (a {@code SELECT COUNT(...)}), not by streaming
     * and counting entities in memory. Filter values are bound as named parameters.</p>
     *
     * @param query the parameterized query describing the filter; must not be {@code null}
     * @return the number of matching {@link Status} entities as an integer
     * @throws ArithmeticException if the count exceeds {@link Integer#MAX_VALUE}
     * @see #fetch(StatusQuery, int, int)
     */
    public int count(StatusQuery query) {
        return (int) Status.count(query.whereClause(), query.params());
    }
}
