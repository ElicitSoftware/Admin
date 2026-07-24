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

import io.quarkus.panache.common.Sort;

import java.util.Map;

/**
 * An immutable, fully parameterized description of a {@link com.elicitsoftware.model.Status}
 * query.
 *
 * <p>This value object carries a Panache/HQL {@code WHERE} fragment together with the named
 * parameter values it references and an optional {@link Sort}. Because every user-supplied
 * value travels through {@link #params()} rather than being concatenated into
 * {@link #whereClause()}, queries built this way are immune to HQL injection.</p>
 *
 * <p>Instances are typically produced by the search UI and consumed by
 * {@link StatusDataSource#fetch(StatusQuery, int, int)} and {@link StatusDataSource#count(StatusQuery)}.</p>
 *
 * @param whereClause a Panache short-form {@code WHERE} fragment (without the {@code WHERE}
 *                    keyword), e.g. {@code "department_id in :departments and lower(token) like :token"};
 *                    referenced values must all appear as named parameters
 * @param params      the named parameter values referenced by {@link #whereClause()}; never
 *                    {@code null} (use an empty map for parameter-free queries)
 * @param sort        the ordering to apply, or {@code null} for the persistence default
 * @author Elicit Software
 * @version 1.0
 * @since 1.0
 * @see StatusDataSource
 */
public record StatusQuery(String whereClause, Map<String, Object> params, Sort sort) {

    /**
     * Canonical constructor that defends against a {@code null} parameter map.
     *
     * @param whereClause the parameterized {@code WHERE} fragment
     * @param params      the named parameter values, coerced to an empty map when {@code null}
     * @param sort        the ordering to apply, or {@code null} for none
     */
    public StatusQuery {
        params = params == null ? Map.of() : params;
    }
}
